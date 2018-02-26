package wifind.wicollect.arithmetic;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import wifind.wicollect.activity.RoomDataBase;

import static wifind.wicollect.activity.RoomDataBase.OriginalMap;

public class FingerPrint {

	private Cursor cursor1,cursor2,cursor3;
	private static final String DATA_TB="dataOfWiCol";        //table name for data
	private static String APTB_NAME="chosenAp";              //table name for AP
	private static String LOCATB_NAME="allLocation";         //table name for location
	private static String FPTB_NAME="fingerPrint";            //table name for fingerPrint
	private static String VIEW_NAME="fp_view";                //name of view to assist producing fingerPrint
	private static String M="FingerPrint";                    //indicate the place of error
			
	public void start(SQLiteDatabase db){
		String x,y,ap,condition,rss;
		ContentValues values;

		Map<String,Map<String,Double>> hashObject = new HashMap<String,Map<String,Double>>();
		//update FPTB_NAME and VIEW_NAME(the aim of later is to avoid deny access to view)
		db.execSQL("drop table if exists " + FPTB_NAME);
		db.execSQL("create table if not exists " + FPTB_NAME
				+ "( id_ integer primary key autoincrement, X_ integer, Y_ integer, mac varchar,rss integer)");
		db.execSQL("drop view if exists " + VIEW_NAME);
		db.execSQL("create view if not exists " + VIEW_NAME 
				+ " as select X_,Y_,mac,avg(rss) from " + DATA_TB + " group by X_,Y_,mac");    //average of rss
		
		//for all location, select relative data about relative AP into FPTB_NAME
		cursor1=db.query(LOCATB_NAME,null,null,null,null,null,null );
		cursor1.moveToFirst();
		while(!cursor1.isAfterLast()){
			x=cursor1.getString(1);
			y=cursor1.getString(2);
			cursor2=db.query(APTB_NAME,null,null,null,null,null,"id_ asc");
			cursor2.moveToFirst();
			while(!cursor2.isAfterLast()){
				ap=cursor2.getString(1);
				condition="X_ =" + x + " and Y_ =" + y + " and mac ='" + ap +"'" ;
				cursor3=db.query(VIEW_NAME,null,condition,null,null,null,null);
				cursor3.moveToFirst();
				while(!cursor3.isAfterLast()){
					rss=cursor3.getString(3);
					values=new ContentValues();
	                values.put("X_", x);
	                values.put("Y_", y);
	                values.put("mac", ap);
	                values.put("rss", rss);
	                long check=db.insert(FPTB_NAME, null, values);
	                if(check==-1)
	                	Log.i(M,"Inserting failure!");
	                else{

						if(hashObject.containsKey("l_"+x+"_"+y)){
							hashObject.get("l_"+x+"_"+y).put(ap, Double.valueOf(rss));
						}else{
							hashObject.put("l_"+x+"_"+y,new HashMap<String, Double>());
							hashObject.get("l_"+x+"_"+y).put(ap, Double.valueOf(rss));
						}

	                	Log.i(M,"Inserting success!" + check);
					}

	                cursor3.moveToNext();
				}
				cursor3.close();
				cursor2.moveToNext();
			}
			cursor2.close();
			cursor1.moveToNext();
		}
		cursor1.close();

		OriginalMap = hashObject;



	}

	class Point {
		public String ap;
		public String rss;


		public Point(String ap, String rss){
			this.ap = ap;
			this.rss = rss;

		}

	}
}