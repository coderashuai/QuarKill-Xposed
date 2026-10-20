package dev.quark.kill;
import android.content.*;
import android.database.*;
import android.net.Uri;
public final class LiteConfigProvider extends ContentProvider {
 private static final java.util.concurrent.atomic.AtomicInteger ERRORS=new java.util.concurrent.atomic.AtomicInteger();
 public static final String AUTH="dev.quark.kill.config";
 public static final String[] KEYS={"plus","voice","camera","ai","swipe","dots","resultvoice","novel","cloud","shortcuts","dns","webads","splash","shake","upsell","update","diagnostic"};
 public static final boolean[] DEFAULTS={false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
 public static Boolean readNullable(Context context,String key){
  if(context==null)return null;
  try(Cursor c=context.getContentResolver().query(Uri.parse("content://"+AUTH+"/flags"),new String[]{key},null,null,null)){
   if(c!=null&&c.moveToFirst())return c.getInt(0)!=0;
  }catch(Throwable t){
   if(ERRORS.getAndIncrement()<3)android.util.Log.e("QuarkKill","Configuration provider query failed",t);
  }
  return null;
 }
 @Override public boolean onCreate(){return true;}
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
  if(!"/flags".equals(uri.getPath())||projection==null||projection.length==0)return null;
  for(String key:projection){
   boolean known=false;
   for(String allowed:KEYS)if(allowed.equals(key)){known=true;break;}
   if(!known)return null;
  }
  MatrixCursor cursor=new MatrixCursor(projection);
  Object[] row=new Object[projection.length];
  android.content.SharedPreferences prefs=getContext().getSharedPreferences("flags",Context.MODE_PRIVATE);
  for(int i=0;i<projection.length;i++){
   int idx=0;
   for(int j=0;j<KEYS.length;j++)if(KEYS[j].equals(projection[i])){idx=j;break;}
   row[i]=prefs.getBoolean(projection[i],DEFAULTS[idx])?1:0;
  }
  cursor.addRow(row);return cursor;
 }
 @Override public String getType(Uri uri){return "vnd.android.cursor.item/vnd.quarklite.flag";}
 @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
