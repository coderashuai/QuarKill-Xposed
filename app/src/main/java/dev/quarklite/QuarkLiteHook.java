package dev.quarklite;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.MotionEvent;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Rect;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.util.WeakHashMap;
import android.widget.TextView;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Quark 10.19 experimental UI-only module. No entitlement, security or network bypass. */
public final class QuarkLiteHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.quark.browser";
    private static final AtomicInteger LOGS = new AtomicInteger();
    private static final AtomicInteger PLUS_MISSES = new AtomicInteger();
    private static final AtomicInteger DNS_BLOCKS = new AtomicInteger();
    private static final AtomicInteger WEB_BLOCKS = new AtomicInteger();
    private static final WeakHashMap<View, Boolean> SEARCH_BOX_GUARDS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> AI_GUARDS = new WeakHashMap<>();
    // Refresh at most once every 2 seconds so Settings changes reach Quark without
    // a process restart while avoiding a ContentProvider IPC on each UI draw.
    private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> FLAGS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile long flagsReadAt;
    private static boolean flag(String key) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - flagsReadAt > 2000L) {
            synchronized (FLAGS) {
                if (now - flagsReadAt > 2000L) {
                    FLAGS.clear();
                    flagsReadAt = now;
                }
            }
        }
        Boolean cached = FLAGS.get(key);
        if (cached != null) return cached;
        android.app.Application app = null;
        try { app = (android.app.Application) XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication");
        } catch (Throwable ignored) {}
        if (app == null) return false;
        boolean value = LiteConfigProvider.read(app, key, false);
        FLAGS.put(key, value);
        return value;
    }
    private static void log(String s) {
        // Production logging: errors only. Avoid startup log I/O and repeated
        // diagnostic formatting on the UI thread.
        if ((s.contains("failed") || s.contains("error") || s.contains("unavailable") ||
            (flag("diagnostic") && s.startsWith("blocked "))) &&
            LOGS.incrementAndGet() <= 40)
            XposedBridge.log("QuarkLite/2.3.8 " + s);
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName) || !TARGET.equals(lp.processName)) return;
        log("injected " + lp.processName);
        hookToolbar(lp.classLoader);
        hookHome(lp.classLoader);
        hookActivityUi(lp.classLoader);
        hookHomeAttach(lp.classLoader);
        hookSearchBox(lp.classLoader);
        hookNetwork();
        hookMenuDialog();
        hookQuickMenuWindows();
    }

    private static void hookToolbar(ClassLoader cl) {
        try {
            Class<?> toolbar = XposedHelpers.findClass("com.ucpro.feature.webwindow.HomeToolbar", cl);
            int count = 0;
            for (Method m : toolbar.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (!"d".equals(m.getName()) || p.length != 2 ||
                    !List.class.isAssignableFrom(p[0]) ||
                    !(p[1] == boolean.class || p[1] == Boolean.class)) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!(param.args[0] instanceof List)) return;
                            ArrayList<Object> filtered = new ArrayList<>();
                            for (Object item : (List<?>) param.args[0]) {
                                String key = itemKey(item);
                                log("toolbar item key=" + key + " class=" + (item == null ? "null" : item.getClass().getName()));
                                if (("choice".equals(key) || "novel".equals(key) ||
                                    "bookstore".equals(key)) && flag("novel") ||
                                    "clouddrive".equals(key) && flag("cloud")) {
                                    log("toolbar filtered " + key);
                                } else filtered.add(item);
                            }
                            param.args[0] = filtered;
                        } catch (Throwable t) { log("toolbar error " + t); }
                    }
                });
                count++;
            }
            log("toolbar methods=" + count);
        } catch (Throwable t) { log("toolbar class unavailable " + t); }
    }

    private static String itemKey(Object item) {
        if (item == null) return "";
        try {
            Method m = item.getClass().getDeclaredMethod("f");
            if (m.getParameterTypes().length != 0 || m.getReturnType() != String.class) return "";
            m.setAccessible(true);
            Object value = m.invoke(item);
            return value == null ? "" : ((String) value).toLowerCase(Locale.ROOT);
        } catch (Throwable t) { return ""; }
    }

    private static void hookHome(ClassLoader cl) {
        try {
            Class<?> home = XposedHelpers.findClass("com.ucpro.feature.homepage.HomePage", cl);
            String[] getters = {"getVoiceIcon", "getCameraIcon", "getPickImageView",
                "getAiAgentTaskFloatPanel", "getChatGuideV2BubbleView", "getDualModeWidget", "getDualModeIndicator"};
            for (String name : getters) {
                try {
                    int count = XposedBridge.hookAllMethods(home, name, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            boolean isDots = name.equals("getDualModeIndicator") || name.equals("getDualModeWidget");
                            String setting = isDots ? "dots" : (name.equals("getVoiceIcon") ? "voice" :
                                (name.equals("getCameraIcon") || name.equals("getPickImageView") ? "camera" : "ai"));
                            if (!flag(setting)) return;
                            Object result = param.getResult();
                            if (!(result instanceof View)) return;
                            View v = (View) result;
                            Runnable hide = () -> {
                                try { v.setVisibility(View.GONE); if (name.equals("getDualModeIndicator") || name.equals("getDualModeWidget") ||
                                name.equals("getVoiceIcon") || name.equals("getCameraIcon") ||
                                name.equals("getPickImageView")) { v.setClickable(false); v.setEnabled(false); } }
                                catch (Throwable t) { log(name + " hide failed " + t); }
                            };
                            if (name.equals("getDualModeIndicator") || name.equals("getDualModeWidget")) {
                                if (Looper.myLooper() == Looper.getMainLooper()) guardAiView(v);
                                else if (Looper.getMainLooper() != null)
                                    new Handler(Looper.getMainLooper()).post(() -> guardAiView(v));
                            }
                            if (Looper.myLooper() == Looper.getMainLooper()) { if (flag(setting)) hide.run(); }
                            else if (Looper.getMainLooper() != null)
                                new Handler(Looper.getMainLooper()).post(() -> { if (flag(setting)) hide.run(); });
                            
                        }
                    }).size();
                    log("hook " + name + "=" + count);
                } catch (Throwable t) { log("hook " + name + " failed " + t); }
            }

            try {
                XposedBridge.hookAllMethods(home, "getNavigationView", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.getResult() instanceof View)) return;
                        View nav = (View) param.getResult();
                        nav.post(() -> installBottomNavigation(nav));
                        nav.postDelayed(() -> installBottomNavigation(nav), 500);
                        nav.postDelayed(() -> installBottomNavigation(nav), 1400);
                    }
                });
                log("novel navigation hook installed");
            } catch (Throwable t) { log("novel navigation hook failed " + t); }
            try {
                XposedBridge.hookAllMethods(home, "dispatchTouchEvent", new XC_MethodHook() {
                    float x, y;
                    boolean suppress;
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!flag("swipe") || param.args.length != 1 || !(param.args[0] instanceof MotionEvent)) return;
                        MotionEvent e = (MotionEvent) param.args[0];
                        switch (e.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                x = e.getX(); y = e.getY(); suppress = false; break;
                            case MotionEvent.ACTION_MOVE:
                                float dx = e.getX() - x, dy = e.getY() - y;
                                // Intercept before the pager reaches its drag threshold, avoiding a visible slide.
                                // Do not intercept rightward gestures (browser back) or vertical scroll.
                                float slop = 6f;
                                if (!(slop > 0f) || slop > 20f) slop = 6f;
                                if (dx < -slop && Math.abs(dx) > 1.15f * Math.abs(dy)) suppress = true;
                                if (suppress) param.setResult(true);
                                break;
                            case MotionEvent.ACTION_UP:
                                if (suppress) param.setResult(true);
                                suppress = false; break;
                            case MotionEvent.ACTION_CANCEL: suppress = false; break;
                        }
                    }
                });
                log("homepage early left-swipe suppression installed");
            } catch (Throwable t) { log("homepage swipe hook failed " + t); }
        } catch (Throwable t) { log("homepage class unavailable " + t); }
    }

    // Keep AI indicator/switch hidden across initial layout and subsequent UI redraws.
    // Register once per View instance to avoid the high-frequency getter log loop.
    private static void guardAiView(View v) {
        if (!flag("dots") || AI_GUARDS.containsKey(v)) return;
        AI_GUARDS.put(v, Boolean.TRUE);
        v.setVisibility(View.GONE);
        v.setClickable(false);
        v.setEnabled(false);
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                view.post(() -> { if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE); });
            }
            @Override public void onViewDetachedFromWindow(View view) {}
        });
        v.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (flag("dots") && v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
                return true;
            }
        });
        log("home control lifecycle guard installed: " + v.getClass().getName());
    }

    private static void hideNovel(View v, int depth) {
        if (!flag("novel") || v == null || depth > 18) return;
        if (v instanceof TextView) {
            CharSequence text = ((TextView) v).getText();
            if (text != null && ("小说".contentEquals(text) || "书城".contentEquals(text))) {
                View target = v;
                if (v.getParent() instanceof View && ((View) v.getParent()).isClickable())
                    target = (View) v.getParent();
                target.setVisibility(View.GONE);
                log("navigation novel shortcut hidden");
                return;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++)
                hideNovel(group.getChildAt(i), depth + 1);
        }
    }




    private static final WeakHashMap<View, Boolean> BOTTOM_NAV_OBSERVERS = new WeakHashMap<>();

    private static void installBottomNavigation(View nav) {
        if (nav == null) return;
        scanBottomNavigation(nav, 0);
        if (BOTTOM_NAV_OBSERVERS.containsKey(nav)) return;
        BOTTOM_NAV_OBSERVERS.put(nav, Boolean.TRUE);
        nav.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,oright,ob) -> scanBottomNavigation(v,0));
    }

    private static void scanBottomNavigation(View v, int depth) {
        if (v == null || depth > 18 || v instanceof WebView) return;
        String label = "";
        CharSequence cd = v.getContentDescription();
        if (cd != null) label = cd.toString().trim();
        if (label.isEmpty() && v instanceof TextView) {
            CharSequence text = ((TextView)v).getText();
            if (text != null) label = text.toString().trim();
        }
        if ("网盘".equals(label) || "夸克网盘".equals(label) ||
            "云盘".equals(label) || "clouddrive".equalsIgnoreCase(label)) {
            if (flag("cloud")) {
                View target = bottomNavItem(v);
                target.setVisibility(View.GONE);
            }
        }

        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup)v;
            for (int i=0;i<g.getChildCount();i++) scanBottomNavigation(g.getChildAt(i),depth+1);
        }
    }

    private static View bottomNavItem(View v) {
        View current = v;
        for (int i=0;i<3;i++) {
            if (!(current.getParent() instanceof View)) break;
            View parent = (View)current.getParent();
            if (parent.isClickable() || parent.isLongClickable()) return parent;
            if (parent instanceof ViewGroup && ((ViewGroup)parent).getChildCount()>3) break;
            current = parent;
        }
        return current;
    }

    // HomePage lifecycle is more reliable than MainActivity.onResume on cold start.
    // Install a one-shot layout observer on the actual homepage view.

    // Hide only the three action tiles within HomeAiSuperInputBox.
    // The unlabeled plus tile is identified by its immediate sibling order
    // relative to the resource-named voice and QR tiles, not absolute pixels.

    // Find the already-created input box from the confirmed HomePage attach callback.
    private static void installSearchBoxFromHome(View home) {
        if (!(home instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) home;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if ("com.ucpro.feature.homepage.widget.HomeAiSuperInputBox".equals(
                    child.getClass().getName())) {
                log("search-box found from homepage child");
                installSearchBoxGuard(child);
                return;
            }
        }
        home.postDelayed(() -> installSearchBoxFromHomeOnce(home), 250L);
    }

    private static void installSearchBoxFromHomeOnce(View home) {
        if (!(home instanceof ViewGroup) || !home.isAttachedToWindow()) return;
        ViewGroup group = (ViewGroup) home;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if ("com.ucpro.feature.homepage.widget.HomeAiSuperInputBox".equals(
                    child.getClass().getName())) {
                log("search-box found from homepage delayed child");
                installSearchBoxGuard(child);
                return;
            }
        }
        log("search-box not found in homepage direct children");
    }

    private static void hookSearchBox(ClassLoader cl) {
        try {
            Class<?> box = XposedHelpers.findClass(
                "com.ucpro.feature.homepage.widget.HomeAiSuperInputBox", cl);
            XposedBridge.hookAllMethods(box, "onAttachedToWindow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!(p.thisObject instanceof View)) return;
                    View v = (View) p.thisObject;
                    v.post(() -> installSearchBoxGuard(v));
                }
            });
            log("search-box attach filter installed");
        } catch (Throwable t) { log("search-box filter failed " + t); }
    }

    private static void installSearchBoxGuard(View box) {
        if (!(box instanceof ViewGroup)) return;
        if (SEARCH_BOX_GUARDS.containsKey(box)) return;
        SEARCH_BOX_GUARDS.put(box, Boolean.TRUE);
        hideSearchBoxActions((ViewGroup) box);
        box.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) ->
            hideSearchBoxActions((ViewGroup) v));
        log("search-box lifecycle guard installed");
    }

    private static void hideSearchBoxActions(ViewGroup box) {
        View voice = null, qr = null;
        for (int i = 0; i < box.getChildCount(); i++) {
            View child = box.getChildAt(i);
            if (child.getId() == View.NO_ID) continue;
            try {
                String id = child.getResources().getResourceEntryName(child.getId());
                if ("home_searchbar_voice_container".equals(id)) voice = child;
                else if ("home_searchbar_qrcode_container".equals(id)) qr = child;
            } catch (Throwable ignored) {}
        }
        // Only act if both exact resource identifiers are present.
        if (voice == null || qr == null) return;
        // Verified v2.0 tree: plus is the rightmost direct FrameLayout child
        // with a direct ImageView. Do not depend on NO_ID or isClickable:
        // either can change between the initial attach and pre-draw.
        View plus = null;
        int bestRight = Integer.MIN_VALUE;
        for (int i = 0; i < box.getChildCount(); i++) {
            View candidate = box.getChildAt(i);
            if (candidate == voice || candidate == qr ||
                !(candidate instanceof android.widget.FrameLayout)) continue;
            ViewGroup group = (ViewGroup) candidate;
            boolean directImage = false;
            for (int j = 0; j < group.getChildCount(); j++) {
                if (group.getChildAt(j) instanceof android.widget.ImageView) {
                    directImage = true;
                    break;
                }
            }
            if (!directImage) continue;
            // The search text wrapper is on the left. The plus tile is
            // in the rightmost third, even after QR and voice disappear.
            int right = candidate.getRight();
            if (right > box.getWidth() * 2 / 3 && right > bestRight) {
                plus = candidate;
                bestRight = right;
            }
        }
        if (plus != null) {
            if (flag("plus")) keepSearchActionHidden(plus, "plus");
        } else if (box.getWidth() > 0 && PLUS_MISSES.getAndIncrement() < 3) {
            log("search-box plus candidate missing children=" + box.getChildCount()
                + " width=" + box.getWidth());
        }
        if (flag("voice")) keepSearchActionHidden(voice, "voice");
        if (flag("camera")) keepSearchActionHidden(qr, "qrcode");
    }

    private static void keepSearchActionHidden(View v, String name) {
        if (v.getVisibility() != View.GONE) {
            v.setVisibility(View.GONE);
            log("search-box action hidden: " + name);
        }
        if (v.isClickable()) v.setClickable(false);
    }

    private static void hookHomeAttach(ClassLoader cl) {
        try {
            Class<?> home = XposedHelpers.findClass("com.ucpro.feature.homepage.HomePage", cl);
            XposedBridge.hookAllMethods(home, "onAttachedToWindow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.thisObject instanceof View)
                        installSearchBoxFromHome((View) p.thisObject);
                }
            });
        } catch (Throwable t) { log("homepage attach hook failed " + t); }
    }

    private static void hookActivityUi(ClassLoader cl) {
        try {
            Class<?> main = XposedHelpers.findClass("com.ucpro.MainActivity", cl);
            XposedBridge.hookAllMethods(main, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!(p.thisObject instanceof Activity)) return;
                    Activity a = (Activity) p.thisObject;
                    View root = a.getWindow().getDecorView();
                    installMenuGuard(root);
                    for (long delay : new long[]{450L, 1700L}) {
                        root.postDelayed(() -> {
                            try {
                                if (!a.isFinishing() && !a.isDestroyed()) {
                                    pruneUi(root, root.getHeight(), 0);
                                }
                            } catch (Throwable t) { log("ui prune error " + t); }
                        }, delay);
                    }
                }
            });
            log("activity UI filter installed");
        } catch (Throwable t) { log("activity UI filter unavailable " + t); }
    }


    private static void pruneUi(View v, int screenHeight, int depth) {
        if (v == null || depth > 35 || screenHeight <= 0) return;
        if (v instanceof android.webkit.WebView) return;
        Rect rect = new Rect();
        if (v.getGlobalVisibleRect(rect)) {
            CharSequence description = v.getContentDescription();
            String desc = description == null ? "" : description.toString().trim();
            boolean bottom = rect.top > screenHeight * 0.79f;
            if (bottom && (desc.equals("小说") || desc.equals("书城") ||
                    desc.equals("语音搜索") || desc.equals("语音输入") ||
                    desc.equals("语音"))) {
                v.setVisibility(View.GONE);
                log("accessibility-labeled UI hidden: " + desc);
                return;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            if (hideTopPagerDots(g, screenHeight)) return;
            for (int i = 0; i < g.getChildCount(); i++)
                pruneUi(g.getChildAt(i), screenHeight, depth + 1);
        }
    }

    private static boolean blockedHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return "doubleclick.net".equals(h) || h.endsWith(".doubleclick.net") ||
               "googlesyndication.com".equals(h) || h.endsWith(".googlesyndication.com") ||
               "googleadservices.com".equals(h) || h.endsWith(".googleadservices.com") ||
               "adnxs.com".equals(h) || h.endsWith(".adnxs.com") ||
               "taboola.com".equals(h) || h.endsWith(".taboola.com") ||
               "outbrain.com".equals(h) || h.endsWith(".outbrain.com") ||
               "track.uc.cn".equals(h) ||
               "sense-arrival.sm.cn".equals(h) ||
               "vs2.gzct.u3.ucweb.com".equals(h);
    }

    private static void hookNetwork() {
        // Best-effort: Java DNS resolution. Chromium's native resolver may bypass it.
        try {
            XposedBridge.hookAllMethods(InetAddress.class, "getAllByName", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0 && p.args[0] instanceof String &&
                        flag("dns") && blockedHost((String) p.args[0])) {
                        int hits=DNS_BLOCKS.incrementAndGet();
                        if(hits<=10)log("blocked DNS " + p.args[0] + " count=" + hits);
                        p.setThrowable(new UnknownHostException((String) p.args[0]));
                    }
                }
            });
            XposedBridge.hookAllMethods(InetAddress.class, "getByName", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0 && p.args[0] instanceof String &&
                        flag("dns") && blockedHost((String) p.args[0])) {
                        log("blocked DNS " + p.args[0]);
                        p.setThrowable(new UnknownHostException((String) p.args[0]));
                    }
                }
            });
            log("tracking and common ad-domain DNS rules installed");
        } catch (Throwable t) { log("DNS hook failed " + t); }

        // Android WebViewClient callbacks; UC/Chromium custom clients may not use these.
        try {
            XposedBridge.hookAllMethods(WebViewClient.class, "shouldInterceptRequest",
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (p.args.length < 2) return;
                        android.net.Uri uri = null;
                        if (p.args[1] instanceof WebResourceRequest)
                            uri = ((WebResourceRequest) p.args[1]).getUrl();
                        else if (p.args[1] instanceof String)
                            uri = android.net.Uri.parse((String) p.args[1]);
                        if (!flag("webads") || uri == null || !blockedHost(uri.getHost())) return;
                        int hits=WEB_BLOCKS.incrementAndGet();
                        if(hits<=10)log("blocked WebView request " + uri.getHost() + " count=" + hits);
                        p.setResult(new WebResourceResponse("text/plain", "UTF-8",
                            new ByteArrayInputStream(new byte[0])));
                    }
                });
            log("WebView request filter installed");
        } catch (Throwable t) { log("WebView filter failed " + t); }
    }
    // Homepage's two-dot AI pager indicator, identified by position and child geometry.
    // This does not disable or replace the pager and never touches browser tabs.
    private static boolean hideTopPagerDots(ViewGroup group, int height) {
        if (!flag("dots") || group.getChildCount() != 2 || height <= 0) return false;
        Rect outer = new Rect();
        if (!group.getGlobalVisibleRect(outer)) return false;
        int width = group.getResources().getDisplayMetrics().widthPixels;
        if (width <= 0 || outer.centerY() < height * 0.065f ||
            outer.centerY() > height * 0.22f ||
            Math.abs(outer.centerX() - width / 2) > width * 0.16f ||
            outer.width() > width * 0.24f || outer.height() > height * 0.045f) return false;
        for (int i = 0; i < 2; i++) {
            View dot = group.getChildAt(i);
            if (dot == null || dot instanceof TextView ||
                dot.getWidth() < 3 || dot.getWidth() > 48 ||
                dot.getHeight() < 3 || dot.getHeight() > 48) return false;
        }
        group.setVisibility(View.GONE);
        group.setClickable(false);
        log("homepage two-dot indicator hidden");
        return true;
    }

    private static final WeakHashMap<View, Boolean> MENU_GUARDS = new WeakHashMap<>();
    private static void installMenuGuard(View decor) {
        if (MENU_GUARDS.containsKey(decor)) return;
        MENU_GUARDS.put(decor, Boolean.TRUE);
        final int[] ticks = {0};
        decor.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,oright,ob) -> {
            if (++ticks[0] % 4 == 0) v.post(() -> hideNovelInMenu(v, 0));
        });
        log("quick-menu decor observer installed root=" + decor.getClass().getName());
    }

    // PopupWindow and custom WindowManager panels do not necessarily call Dialog.show().
    // Observe newly attached window roots; diagnostics are limited to menu labels and classes.
    private static void watchMenuWindow(View root, String source) {
        if (root == null) return;
        Runnable scan = () -> {
            try {
                installMenuGuard(root);
                hideNovelInMenu(root, 0);
            } catch (Throwable t) { log("menu-window scan failed " + t.getClass().getSimpleName()); }
        };
        root.post(scan);
        root.postDelayed(scan, 300);
        root.postDelayed(scan, 1000);
    }
    private static void hookQuickMenuWindows() {
        try {
            Class<?> impl = XposedHelpers.findClass("android.view.WindowManagerImpl", null);
            XposedBridge.hookAllMethods(impl, "addView", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0 && p.args[0] instanceof View)
                        watchMenuWindow((View)p.args[0], "window-add");
                }
            });
            log("menu-window WindowManager hook installed");
        } catch (Throwable t) { log("menu-window WindowManager hook failed " + t); }
        try {
            XposedBridge.hookAllMethods(android.widget.PopupWindow.class, "showAtLocation", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    android.widget.PopupWindow popup = (android.widget.PopupWindow)p.thisObject;
                    watchMenuWindow(popup.getContentView(), "popup-at-location");
                }
            });
            XposedBridge.hookAllMethods(android.widget.PopupWindow.class, "showAsDropDown", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    android.widget.PopupWindow popup = (android.widget.PopupWindow)p.thisObject;
                    watchMenuWindow(popup.getContentView(), "popup-dropdown");
                }
            });
            log("menu-window PopupWindow hooks installed");
        } catch (Throwable t) { log("menu-window PopupWindow hook failed " + t); }
    }

    private static void hookMenuDialog() {
        try {
            XposedBridge.hookAllMethods(Dialog.class, "show", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!(p.thisObject instanceof Dialog)) return;
                    Dialog dialog = (Dialog) p.thisObject;
                    if (dialog.getWindow() == null) return;
                    View root = dialog.getWindow().getDecorView();
                    root.post(() -> { hideNovelInMenu(root, 0); });
                    root.postDelayed(() -> { hideNovelInMenu(root, 0); }, 250);
                    root.postDelayed(() -> { hideNovelInMenu(root, 0); }, 800);
                    installMenuGuard(root);
                }
            });
            log("quick-menu dialog hook installed");
        } catch (Throwable t) { log("quick-menu dialog hook failed " + t); }
    }

    // Only hide a tile when its nearby menu labels confirm the exact quick-menu.
    // Never scan WebViews or hide generic page content with the word "小说".
    private static boolean isMenuLabel(String text) {
        return "书签".equals(text) || "下载".equals(text) || "文件".equals(text) ||
            "网盘".equals(text) || "刷新".equals(text) || "历史".equals(text) ||
            "加入书签".equals(text) || "多窗口".equals(text) || "暗夜模式".equals(text) ||
            "无痕".equals(text);
    }
    private static final WeakHashMap<ViewGroup, Boolean> MENU_COMPACTION = new WeakHashMap<>();
    private static void installMenuCompaction(ViewGroup parent) {
        if (!parent.getClass().getName().contains("HorizontalAverageLayout")) return;
        if (MENU_COMPACTION.containsKey(parent)) return;
        MENU_COMPACTION.put(parent, Boolean.TRUE);
        final ViewGroup row = parent;
        row.getViewTreeObserver().addOnPreDrawListener(() -> {
            try {
                int count = row.getChildCount();
                if (count < 2 || count > 5 || row.getWidth() <= 0) return true;
                // HorizontalAverageLayout reserves five evenly spaced slots.
                // Keep the original item sizes, but center each surviving item
                // in one of 'count' equally sized slots across the entire row.
                float available = row.getWidth() - row.getPaddingLeft() - row.getPaddingRight();
                if (available <= 0) return true;
                float slot = available / count;
                for (int i = 0; i < count; i++) {
                    View child = row.getChildAt(i);
                    if (child.getVisibility() == View.GONE || child.getWidth() <= 0) continue;
                    float desiredLeft = row.getPaddingLeft() + slot * (i + .5f) - child.getWidth() / 2f;
                    float translation = desiredLeft - child.getLeft();
                    if (Math.abs(child.getTranslationX() - translation) > .5f)
                        child.setTranslationX(translation);
                }
            } catch (Throwable ignored) {}
            return true;
        });
        log("menu-compaction equal-spacing installed parent=" + row.getClass().getName());
    }
    private static final AtomicInteger MENU_LAYOUT_DIAGS = new AtomicInteger();
    private static void hideNovelInMenu(View v, int depth) {
        if (v == null || depth > 24 || v instanceof WebView) return;
        if (!(v instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) v;
        // The quick-menu grid contains the novel label and several sibling actions.
        java.util.ArrayList<View> novelLabels = new java.util.ArrayList<>();
        int neighbors = countMenuLabels(group, 0, novelLabels);
        if (neighbors >= 3 && !novelLabels.isEmpty()) {
            for (View label : novelLabels) {
                View tile = findMenuTile(label, group);
                if (tile != null && tile.getVisibility() != View.GONE) {
                    tile.setVisibility(View.GONE);
                    tile.setClickable(false);
                    // Removing a child does not compact fixed-position grids; capture parent layout for a targeted fix.
                    if (tile.getParent() instanceof ViewGroup) {
                        ViewGroup parent = (ViewGroup) tile.getParent();
                        if (flag("diagnostic") && MENU_LAYOUT_DIAGS.getAndIncrement() < 8) {
                            ViewGroup.LayoutParams lp=tile.getLayoutParams();
                            log("menu-layout parent=" + parent.getClass().getName() +
                                " childCount=" + parent.getChildCount() + " index=" + parent.indexOfChild(tile) +
                                " lp=" + (lp==null?"null":lp.getClass().getName()));
                        }
                        parent.removeView(tile);
                        parent.requestLayout();
                        installMenuCompaction(parent);
                    }
                    log("quick-menu shortcut compacted label=" + ((TextView)label).getText() + " class=" + tile.getClass().getName());
                }
            }
            return;
        }
        for (int i = 0; i < group.getChildCount(); i++)
            hideNovelInMenu(group.getChildAt(i), depth + 1);
    }
    private static int countMenuLabels(View v, int depth, java.util.List<View> novels) {
        if (depth > 5 || v instanceof WebView || v.getVisibility() != View.VISIBLE) return 0;
        if (v instanceof TextView) {
            String label = String.valueOf(((TextView)v).getText()).trim();
            if (("小说".equals(label) && flag("novel")) ||
                ("壁纸".equals(label) || "主题壁纸".equals(label))) novels.add(v);
            return isMenuLabel(label) ? 1 : 0;
        }
        int found = 0;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup)v;
            for (int i=0;i<g.getChildCount();i++)
                found += countMenuLabels(g.getChildAt(i),depth+1,novels);
        }
        return found;
    }
    private static View findMenuTile(View label, ViewGroup boundary) {
        View current = label;
        for (int i=0;i<5 && current.getParent() instanceof View;i++) {
            View parent = (View)current.getParent();
            if (parent == boundary) return current;
            if (parent.isClickable()) return parent;
            if (parent instanceof ViewGroup && ((ViewGroup)parent).getChildCount() > 5)
                return current == label ? null : current;
            current = parent;
        }
        return null;
    }
}
