package dev.quarklite;
import android.content.*;
import android.database.*;
import android.net.Uri;
public final class LiteConfigProvider extends ContentProvider {
 public static final String AUTH="dev.quarklite.config";
 public static final String[] KEYS={"plus","voice","camera","ai","swipe","dots","resultvoice","novel","cloud","shortcuts","dns","webads","splash","shake","upsell","update","diagnostic"};
 public static final boolean[] DEFAULTS={false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
 public static Boolean readNullable(Context context,String key){
  if(context==null)return null;
  try(Cursor c=context.getContentResolver().query(Uri.parse("content://"+AUTH+"/flags"),new String[]{key},null,null,null)){
   if(c!=null&&c.moveToFirst())return c.getInt(0)!=0;
  }catch(Throwable ignored){}
  return null;
 }
 @Override public boolean onCreate(){return true;}
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
  if(!"/flags".equals(uri.getPath())||projection==null||projection.length!=1)return null;
  String key=projection[0];int idx=-1;
  for(int i=0;i<KEYS.length;i++)if(KEYS[i].equals(key)){idx=i;break;}
  if(idx<0)return null;
  MatrixCursor cursor=new MatrixCursor(new String[]{key});
  boolean value=getContext().getSharedPreferences("flags",Context.MODE_PRIVATE).getBoolean(key,DEFAULTS[idx]);
  cursor.addRow(new Object[]{value?1:0});return cursor;
 }
 @Override public String getType(Uri uri){return "vnd.android.cursor.item/vnd.quarklite.flag";}
 @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
