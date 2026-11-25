// Big clock + music remote for Pebble Time Steel (Basalt).
#include <pebble.h>

// Buttons:  UP = previous   SELECT = play/pause   DOWN = next   BACK = exit
//
// Watch -> phone: key COMMAND set to a uint8:
//   1 = previous, 2 = play/pause, 3 = next, 4 = sync (sent once at startup,
//   asks the phone to send the current track, theme and volume),
//   5 = volume up, 6 = volume down (long press Up / Down; repeats while held)
// Phone -> watch:
//   THEME  = uint8, 0 (dark) or 1 (light); saved on the watch
//   TITLE  = current track title (string)
//   ARTIST = current track artist (string)
//   VOLUME = uint8, music volume as a percentage, 0 to 100
//
// AppMessage keys are plain numbers defined below. They must match Config.kt
// in the Android app. This file does not use the project's "message keys"
// setting, so that setting can stay empty.
// Do NOT add a src/pkjs/index.js file.

#define KEY_COMMAND 0
#define KEY_THEME   1
#define KEY_ARTIST  2
#define KEY_TITLE   3
#define KEY_VOLUME  4

#define CMD_PREV       1
#define CMD_PLAY_PAUSE 2
#define CMD_NEXT       3
#define CMD_SYNC       4
#define CMD_VOL_UP     5
#define CMD_VOL_DOWN   6

#define THEME_DARK  0
#define THEME_LIGHT 1

// Theme used until the phone app sends one. Change to THEME_LIGHT to try
// the light theme without the phone app.
#define DEFAULT_THEME THEME_DARK

#define PERSIST_KEY_THEME 1

// Width of the on-screen button strip on the right edge.
#define BAR_W 24

// If the clock looks clipped, try FONT_KEY_BITHAM_34_MEDIUM_NUMBERS instead.
#define CLOCK_FONT FONT_KEY_BITHAM_42_BOLD

// Track title and artist. If a long title looks cramped, go back to
// FONT_KEY_GOTHIC_24_BOLD for the title.
#define TITLE_FONT  FONT_KEY_GOTHIC_28_BOLD
#define ARTIST_FONT FONT_KEY_GOTHIC_18_BOLD

// Hold Up / Down this long (ms) to start changing the volume, which then
// repeats every VOL_REPEAT_MS while the button stays held.
#define LONG_PRESS_MS 400
#define VOL_REPEAT_MS 350

// Height of the volume bar along the bottom edge.
#define VOLUME_BAR_H 8

static Window *s_window;
static TextLayer *s_time_layer;
static TextLayer *s_title_layer;
static TextLayer *s_artist_layer;
static TextLayer *s_status_layer;
static Layer *s_buttons_layer;
static Layer *s_volume_layer;
static AppTimer *s_clear_timer;
static AppTimer *s_press_timer;
static AppTimer *s_sync_timer;
static AppTimer *s_repeat_timer;
static int s_repeat_cmd = 0;
static int s_volume = -1;  // music volume in percent; -1 until the phone tells us
static int s_pressed = 0;  // 0 = none, otherwise the CMD_* of the pressed button
static int s_theme = DEFAULT_THEME;

static char s_title_buf[128] = "Not playing";
static char s_artist_buf[128] = "";

// Icon shapes, relative to the point they are moved to.
static GPoint s_left_pts[3]  = {{6, -6}, {6, 6}, {-5, 0}};
static GPoint s_right_pts[3] = {{-6, -6}, {-6, 6}, {5, 0}};
static GPoint s_play_pts[3]  = {{-8, -6}, {-8, 6}, {0, 0}};
static const GPathInfo s_left_info  = { .num_points = 3, .points = s_left_pts };
static const GPathInfo s_right_info = { .num_points = 3, .points = s_right_pts };
static const GPathInfo s_play_info  = { .num_points = 3, .points = s_play_pts };
static GPath *s_tri_left;
static GPath *s_tri_right;
static GPath *s_tri_play;

// ---------- Theme ----------

typedef struct {
  GColor bg;
  GColor text;
  GColor subtext;
  GColor status;
  GColor bar_bg;
  GColor icon;
  GColor pressed;
} Theme;

static Theme current_theme(void) {
  Theme t;
  if (s_theme == THEME_LIGHT) {
    t.bg = GColorWhite;
    t.text = GColorBlack;
    t.subtext = GColorDarkGray;
    t.status = GColorDarkGreen;
    t.bar_bg = GColorLightGray;
    t.icon = GColorBlack;
    t.pressed = GColorDarkGreen;
  } else {
    t.bg = GColorBlack;
    t.text = GColorWhite;
    t.subtext = GColorLightGray;
    t.status = GColorMediumSpringGreen;
    t.bar_bg = GColorDarkGray;
    t.icon = GColorWhite;
    t.pressed = GColorMediumSpringGreen;
  }
  return t;
}

static void apply_theme(void) {
  if (!s_time_layer) {
    return;  // window not loaded yet
  }
  Theme t = current_theme();
  window_set_background_color(s_window, t.bg);
  text_layer_set_text_color(s_time_layer, t.text);
  text_layer_set_text_color(s_title_layer, t.text);
  text_layer_set_text_color(s_artist_layer, t.subtext);
  text_layer_set_text_color(s_status_layer, t.status);
  layer_mark_dirty(s_buttons_layer);
  layer_mark_dirty(s_volume_layer);
}

static void set_theme(int theme) {
  s_theme = (theme == THEME_LIGHT) ? THEME_LIGHT : THEME_DARK;
  persist_write_int(PERSIST_KEY_THEME, s_theme);
  apply_theme();
}

// ---------- Clock ----------

static void update_time(void) {
  time_t now = time(NULL);
  struct tm *t = localtime(&now);

  static char s_time_buf[8];

  bool is_24h = clock_is_24h_style();
  strftime(s_time_buf, sizeof(s_time_buf), is_24h ? "%H:%M" : "%I:%M", t);
  // Drop the leading zero in 12-hour mode (09:05 -> 9:05)
  const char *shown = s_time_buf;
  if (!is_24h && s_time_buf[0] == '0') {
    shown = s_time_buf + 1;
  }
  text_layer_set_text(s_time_layer, shown);
}

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  update_time();
}

// ---------- Now playing ----------

static void set_now_playing(const char *title, const char *artist) {
  if (title && title[0]) {
    snprintf(s_title_buf, sizeof(s_title_buf), "%s", title);
  } else {
    snprintf(s_title_buf, sizeof(s_title_buf), "Not playing");
  }
  snprintf(s_artist_buf, sizeof(s_artist_buf), "%s", artist ? artist : "");

  if (s_title_layer) {
    text_layer_set_text(s_title_layer, s_title_buf);
  }
  if (s_artist_layer) {
    text_layer_set_text(s_artist_layer, s_artist_buf);
  }
}

// ---------- Status line ----------

static void clear_status(void *data) {
  s_clear_timer = NULL;
  text_layer_set_text(s_status_layer, "");
}

static void show_status(const char *text) {
  text_layer_set_text(s_status_layer, text);
  if (s_clear_timer) {
    app_timer_cancel(s_clear_timer);
  }
  s_clear_timer = app_timer_register(2000, clear_status, NULL);
}

// ---------- On-screen buttons ----------

static void buttons_update_proc(Layer *layer, GContext *ctx) {
  GRect b = layer_get_bounds(layer);
  Theme t = current_theme();

  graphics_context_set_fill_color(ctx, t.bar_bg);
  graphics_fill_rect(ctx, b, 0, GCornerNone);

  int cx = b.size.w / 2;
  // Vertical positions line up with the Up / Select / Down buttons.
  int y_up = 24;
  int y_sel = b.size.h / 2;
  int y_down = b.size.h - 24;

  // Previous: bar + left-pointing triangle
  graphics_context_set_fill_color(ctx, s_pressed == CMD_PREV ? t.pressed : t.icon);
  graphics_fill_rect(ctx, GRect(cx - 8, y_up - 6, 2, 12), 0, GCornerNone);
  gpath_move_to(s_tri_left, GPoint(cx + 1, y_up));
  gpath_draw_filled(ctx, s_tri_left);

  // Play / pause: triangle + two bars
  graphics_context_set_fill_color(ctx, s_pressed == CMD_PLAY_PAUSE ? t.pressed : t.icon);
  gpath_move_to(s_tri_play, GPoint(cx - 1, y_sel));
  gpath_draw_filled(ctx, s_tri_play);
  graphics_fill_rect(ctx, GRect(cx + 2, y_sel - 6, 2, 12), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(cx + 6, y_sel - 6, 2, 12), 0, GCornerNone);

  // Next: right-pointing triangle + bar
  graphics_context_set_fill_color(ctx, s_pressed == CMD_NEXT ? t.pressed : t.icon);
  gpath_move_to(s_tri_right, GPoint(cx - 1, y_down));
  gpath_draw_filled(ctx, s_tri_right);
  graphics_fill_rect(ctx, GRect(cx + 6, y_down - 6, 2, 12), 0, GCornerNone);
}

static void clear_pressed(void *data) {
  s_press_timer = NULL;
  s_pressed = 0;
  layer_mark_dirty(s_buttons_layer);
}

static void flash_button(int button) {
  s_pressed = button;
  layer_mark_dirty(s_buttons_layer);
  if (s_press_timer) {
    app_timer_cancel(s_press_timer);
  }
  s_press_timer = app_timer_register(200, clear_pressed, NULL);
}

// ---------- Volume bar ----------

static void volume_update_proc(Layer *layer, GContext *ctx) {
  GRect b = layer_get_bounds(layer);
  Theme t = current_theme();

  // Empty track, then the filled part on top.
  graphics_context_set_fill_color(ctx, t.bar_bg);
  graphics_fill_rect(ctx, b, 2, GCornersAll);

  if (s_volume > 0) {
    int fill_w = (b.size.w * s_volume) / 100;
    if (fill_w > 0) {
      graphics_context_set_fill_color(ctx, t.status);
      graphics_fill_rect(ctx, GRect(0, 0, fill_w, b.size.h), 2, GCornersAll);
    }
  }
}

static void set_volume(int percent) {
  if (percent < 0) {
    percent = 0;
  }
  if (percent > 100) {
    percent = 100;
  }
  s_volume = percent;
  if (s_volume_layer) {
    layer_mark_dirty(s_volume_layer);
  }
}

// ---------- Messages with the phone ----------

static AppMessageResult send_int(int32_t command) {
  DictionaryIterator *iter;
  AppMessageResult result = app_message_outbox_begin(&iter);
  if (result != APP_MSG_OK) {
    return result;
  }
  dict_write_uint8(iter, KEY_COMMAND, (uint8_t) command);
  dict_write_end(iter);
  return app_message_outbox_send();
}

// label = NULL sends quietly (used for the repeats while a button is held).
static void send_command(int32_t command, const char *label) {
  // Volume commands light up the Up / Down icon of the button being held.
  int icon = (int) command;
  if (command == CMD_VOL_UP) {
    icon = CMD_PREV;
  } else if (command == CMD_VOL_DOWN) {
    icon = CMD_NEXT;
  }
  flash_button(icon);

  AppMessageResult result = send_int(command);
  if (!label) {
    return;
  }
  if (result == APP_MSG_BUSY) {
    show_status("Busy, try again");
  } else if (result != APP_MSG_OK) {
    show_status("Send failed");
  } else {
    show_status(label);
  }
}

static void sync_timer_callback(void *data) {
  s_sync_timer = NULL;
  send_int(CMD_SYNC);
}

static void outbox_failed_handler(DictionaryIterator *iter,
                                  AppMessageResult reason, void *context) {
  show_status("Phone not reachable");
}

static void inbox_received_handler(DictionaryIterator *iter, void *context) {
  Tuple *theme_tuple = dict_find(iter, KEY_THEME);
  if (theme_tuple) {
    set_theme((int) theme_tuple->value->uint8);
  }

  Tuple *title_tuple = dict_find(iter, KEY_TITLE);
  Tuple *artist_tuple = dict_find(iter, KEY_ARTIST);
  if (title_tuple || artist_tuple) {
    set_now_playing(title_tuple ? title_tuple->value->cstring : "",
                    artist_tuple ? artist_tuple->value->cstring : "");
  }

  Tuple *volume_tuple = dict_find(iter, KEY_VOLUME);
  if (volume_tuple) {
    set_volume((int) volume_tuple->value->uint8);
  }
}

// ---------- Buttons ----------

static void up_click_handler(ClickRecognizerRef recognizer, void *context) {
  send_command(CMD_PREV, "Previous");
}

static void select_click_handler(ClickRecognizerRef recognizer, void *context) {
  send_command(CMD_PLAY_PAUSE, "Play / Pause");
}

static void down_click_handler(ClickRecognizerRef recognizer, void *context) {
  send_command(CMD_NEXT, "Next");
}

// Long press: Up = volume up, Down = volume down. Keeps stepping while held.
static void stop_repeat(void) {
  if (s_repeat_timer) {
    app_timer_cancel(s_repeat_timer);
    s_repeat_timer = NULL;
  }
}

static void repeat_callback(void *data) {
  s_repeat_timer = app_timer_register(VOL_REPEAT_MS, repeat_callback, NULL);
  send_command(s_repeat_cmd, NULL);
}

static void start_repeat(int cmd, const char *label) {
  stop_repeat();
  s_repeat_cmd = cmd;
  send_command(cmd, label);
  s_repeat_timer = app_timer_register(VOL_REPEAT_MS, repeat_callback, NULL);
}

static void up_long_press_handler(ClickRecognizerRef recognizer, void *context) {
  start_repeat(CMD_VOL_UP, "Volume up");
}

static void down_long_press_handler(ClickRecognizerRef recognizer, void *context) {
  start_repeat(CMD_VOL_DOWN, "Volume down");
}

static void long_press_release_handler(ClickRecognizerRef recognizer, void *context) {
  stop_repeat();
}

static void click_config_provider(void *context) {
  window_single_click_subscribe(BUTTON_ID_UP, up_click_handler);
  window_single_click_subscribe(BUTTON_ID_SELECT, select_click_handler);
  window_single_click_subscribe(BUTTON_ID_DOWN, down_click_handler);
  window_long_click_subscribe(BUTTON_ID_UP, LONG_PRESS_MS,
                              up_long_press_handler, long_press_release_handler);
  window_long_click_subscribe(BUTTON_ID_DOWN, LONG_PRESS_MS,
                              down_long_press_handler, long_press_release_handler);
}

// ---------- Window ----------

static TextLayer *make_text_layer(Layer *parent, GRect frame, const char *font_key) {
  TextLayer *layer = text_layer_create(frame);
  text_layer_set_background_color(layer, GColorClear);
  text_layer_set_font(layer, fonts_get_system_font(font_key));
  text_layer_set_text_alignment(layer, GTextAlignmentCenter);
  layer_add_child(parent, text_layer_get_layer(layer));
  return layer;
}

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  GRect bounds = layer_get_bounds(root);
  int w = bounds.size.w;
  int h = bounds.size.h;
  int content_w = w - BAR_W;

  // Top to bottom: clock, track title (up to 2 lines), artist, status text,
  // and the volume bar along the bottom edge.
  int bar_y = h - VOLUME_BAR_H - 3;

  s_time_layer = make_text_layer(root, GRect(0, 2, content_w, 50), CLOCK_FONT);

  s_title_layer = make_text_layer(root, GRect(2, 52, content_w - 4, 64), TITLE_FONT);
  text_layer_set_overflow_mode(s_title_layer, GTextOverflowModeTrailingEllipsis);

  s_artist_layer = make_text_layer(root, GRect(2, 116, content_w - 4, 22), ARTIST_FONT);
  text_layer_set_overflow_mode(s_artist_layer, GTextOverflowModeTrailingEllipsis);

  s_status_layer = make_text_layer(root, GRect(0, bar_y - 18, content_w, 18),
                                   FONT_KEY_GOTHIC_14_BOLD);

  s_volume_layer = layer_create(GRect(4, bar_y, content_w - 8, VOLUME_BAR_H));
  layer_set_update_proc(s_volume_layer, volume_update_proc);
  layer_add_child(root, s_volume_layer);

  text_layer_set_text(s_title_layer, s_title_buf);
  text_layer_set_text(s_artist_layer, s_artist_buf);

  s_tri_left = gpath_create(&s_left_info);
  s_tri_right = gpath_create(&s_right_info);
  s_tri_play = gpath_create(&s_play_info);

  s_buttons_layer = layer_create(GRect(w - BAR_W, 0, BAR_W, h));
  layer_set_update_proc(s_buttons_layer, buttons_update_proc);
  layer_add_child(root, s_buttons_layer);

  apply_theme();
  update_time();
}

static void window_unload(Window *window) {
  text_layer_destroy(s_time_layer);
  text_layer_destroy(s_title_layer);
  text_layer_destroy(s_artist_layer);
  text_layer_destroy(s_status_layer);
  s_time_layer = NULL;
  s_title_layer = NULL;
  s_artist_layer = NULL;
  s_status_layer = NULL;
  layer_destroy(s_buttons_layer);
  s_buttons_layer = NULL;
  layer_destroy(s_volume_layer);
  s_volume_layer = NULL;
  gpath_destroy(s_tri_left);
  gpath_destroy(s_tri_right);
  gpath_destroy(s_tri_play);
}

// ---------- App lifecycle ----------

static void init(void) {
  if (persist_exists(PERSIST_KEY_THEME)) {
    s_theme = persist_read_int(PERSIST_KEY_THEME) == THEME_LIGHT ? THEME_LIGHT
                                                                : THEME_DARK;
  }

  s_window = window_create();
  window_set_click_config_provider(s_window, click_config_provider);
  window_set_window_handlers(s_window, (WindowHandlers) {
    .load = window_load,
    .unload = window_unload,
  });
  window_stack_push(s_window, true);

  tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);

  app_message_register_outbox_failed(outbox_failed_handler);
  app_message_register_inbox_received(inbox_received_handler);
  app_message_open(512, 64);

  // Ask the phone for the current track and theme shortly after startup.
  s_sync_timer = app_timer_register(500, sync_timer_callback, NULL);
}

static void deinit(void) {
  if (s_clear_timer) {
    app_timer_cancel(s_clear_timer);
  }
  if (s_press_timer) {
    app_timer_cancel(s_press_timer);
  }
  if (s_sync_timer) {
    app_timer_cancel(s_sync_timer);
  }
  stop_repeat();
  tick_timer_service_unsubscribe();
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
