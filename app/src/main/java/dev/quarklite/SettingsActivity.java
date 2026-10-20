package dev.quarklite;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import android.view.*;
import android.widget.*;
public final class SettingsActivity extends Activity {
 private final String[][] sections={
  {"首页精简","plus","ai","swipe"},
  {"搜索与菜单","novel","cloud"},
  {"广告与推广","dns","webads"},
  {"界面与维护","diagnostic"}
 };
 private final String[] names={"隐藏搜索框加号","隐藏搜索框语音","隐藏搜索框相机","隐藏首页 AI 入口","禁止左滑进入 AI","隐藏双模式指示点","隐藏搜索结果页语音","隐藏小说／书城入口","隐藏网盘推广入口","隐藏其他快捷入口","拦截广告域名","过滤网页广告请求","屏蔽开屏广告","屏蔽摇一摇跳转","隐藏会员推广提示","隐藏非必要更新推荐","详细调试日志"};
 private int index(String key){for(int i=0;i<LiteConfigProvider.KEYS.length;i++)if(LiteConfigProvider.KEYS[i].equals(key))return i;return -1;}
 private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
 @Override public void onCreate(Bundle b){super.onCreate(b);render();new Handler(Looper.getMainLooper()).postDelayed(this::launchSupportSafely,350);}
 private void render(){
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(20),dp(20),dp(30));root.setBackgroundColor(Color.rgb(248,249,252));
  scroll.addView(root);setContentView(scroll);
  TextView title=new TextView(this);title.setText("Quark Kill");title.setTextSize(27);title.setTextColor(Color.rgb(28,35,48));title.setTypeface(null,android.graphics.Typeface.BOLD);root.addView(title);
  TextView sub=new TextView(this);sub.setText("功能精简设置 · 更改后请强制停止并重新打开夸克");sub.setTextSize(13);sub.setTextColor(Color.DKGRAY);root.addView(sub);
  for(String[] section:sections){
   TextView heading=new TextView(this);heading.setText(section[0]);heading.setTextSize(17);heading.setTypeface(null,android.graphics.Typeface.BOLD);heading.setTextColor(Color.rgb(42,55,83));heading.setPadding(0,dp(24),0,dp(9));root.addView(heading);
   for(int j=1;j<section.length;j++){
    String key=section[j];int idx=index(key);if(idx<0)continue;
    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(6),dp(8));row.setBackgroundColor(Color.WHITE);
    TextView name=new TextView(this);name.setText("plus".equals(key)?"隐藏搜索框小插件（同时隐藏语音和相机）":("swipe".equals(key)?"禁止左滑进入 AI（同时隐藏模式指示点）":("cloud".equals(key)?"隐藏首页底栏网盘按钮":("dns".equals(key)?"禁止检测和谐网址(待实现)":("webads".equals(key)?"实验性网页请求过滤":names[idx])))));name.setTextSize(14);name.setTextColor(Color.rgb(34,40,52));
    row.addView(name,new LinearLayout.LayoutParams(0,dp(48),1));
    Switch toggle=new Switch(this);
    toggle.setChecked(getSharedPreferences("flags",0).getBoolean(key,false));
    toggle.setOnCheckedChangeListener((button,checked)->{
     android.content.SharedPreferences.Editor edit=getSharedPreferences("flags",0).edit().putBoolean(key,checked);
     if("plus".equals(key)){edit.putBoolean("voice",checked).putBoolean("camera",checked);}
     if("swipe".equals(key)){edit.putBoolean("dots",checked);}
     edit.apply();
    });
    row.addView(toggle);root.addView(row);
    View sep=new View(this);sep.setBackgroundColor(Color.rgb(237,239,244));root.addView(sep,new LinearLayout.LayoutParams(-1,dp(1)));
   }
  }
  Button reset=new Button(this);reset.setText("全部恢复关闭");reset.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("恢复默认设置").setMessage("将关闭所有精简功能，重新打开夸克后生效。").setNegativeButton("取消",null).setPositiveButton("恢复", (d,w)->{getSharedPreferences("flags",0).edit().clear().apply();render();}).show());root.addView(reset);
  Button support=new Button(this);support.setText("支持作者 · 自愿打赏");support.setOnClickListener(v->showSupportDialog());root.addView(support);
 }
 private void launchSupportSafely(){
  try{showSupportDialog();}catch(Throwable t){android.util.Log.e("QuarkKill","Donation dialog failed",t);}
 }
 private TextView label(String value,int size){
  TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
  t.setTextColor(Color.rgb(34,40,52));t.setPadding(dp(4),dp(8),dp(4),dp(8));return t;
 }
 private ImageView qrImage(String payload){
  ImageView view=new ImageView(this);
  try {
   BitMatrix matrix=new MultiFormatWriter().encode(payload,BarcodeFormat.QR_CODE,480,480);
   int w=matrix.getWidth(),h=matrix.getHeight();
   int[] pixels=new int[w*h];
   for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
   Bitmap bitmap=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
   view.setImageBitmap(bitmap);
  } catch(Throwable e){view.setContentDescription("收款二维码生成失败");android.util.Log.e("QuarkKill","QR rendering failed",e);}
  view.setAdjustViewBounds(true);view.setScaleType(ImageView.ScaleType.FIT_CENTER);
  return view;
 }
 private void showSupportDialog(){
  if(isFinishing() || isDestroyed())return;
  ScrollView scroll=new ScrollView(this);
  LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(8),dp(16),dp(8));
  scroll.addView(body);
  body.addView(label("Quark Kill 致力于让夸克浏览器更简洁、清爽。已提供首页搜索框精简、AI 入口隐藏、小说入口精简，以及部分广告域名和网页广告请求过滤等功能。\n\n如果这些功能改善了你的使用体验，欢迎自愿打赏作者，支持后续维护与适配。打赏完全自愿，不影响任何功能。",14));
  body.addView(label("微信支付",17));
  body.addView(qrImage("wxp://f2f0wrtfJpeWX1W9fzB40NJW-R-me0VIclz9LAZUX9Z8HdpZhgWlmd8rhUjHGVbrel97"),new LinearLayout.LayoutParams(-1,dp(250)));
  body.addView(label("支付宝",17));
  body.addView(qrImage("https://qr.alipay.com/2m6134634bmmmidvja5irc0"),new LinearLayout.LayoutParams(-1,dp(250)));
  body.addView(label("感谢支持！可截图二维码后使用微信或支付宝扫一扫识别。",12));
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("支持 Quark Kill 持续维护").setView(scroll)
   .setPositiveButton("5 秒后可关闭",null).create();
  dialog.setOnShowListener(d->{
   final Button close=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
   close.setEnabled(false);
   final Handler handler=new Handler(Looper.getMainLooper());
   final long deadline=android.os.SystemClock.uptimeMillis()+5000L;
   final Runnable[] tick=new Runnable[1];
   tick[0]=()->{
    if(!dialog.isShowing())return;
    long remaining=Math.max(0L,deadline-android.os.SystemClock.uptimeMillis());
    if(remaining==0){close.setText("关闭");close.setEnabled(true);dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(true);}
    else {close.setText(((remaining+999)/1000)+" 秒后可关闭");handler.postDelayed(tick[0],200L);}
   };
   tick[0].run();
  });
  dialog.setCancelable(false);dialog.setCanceledOnTouchOutside(false);
  dialog.show();
  dialog.setOnKeyListener((d,key,event)->key==KeyEvent.KEYCODE_BACK &&
   (dialog.getButton(AlertDialog.BUTTON_POSITIVE)==null ||
    !dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled()));
 }

}
