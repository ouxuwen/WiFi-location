package wifind.wicollect.activity;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import wifind.wicollect.R;
import wifind.wicollect.arithmetic.FingerPrint;
import wifind.wicollect.sqlite.DB_helper;

import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TabHost.OnTabChangeListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.facebook.stetho.Stetho;
import com.facebook.stetho.okhttp3.StethoInterceptor;


public class WiCol_Activity extends Activity {

	private int whichTab=0;                              //which tab now
	private EditText et_x,et_y;
	private TextView group_tv;                           //group TextView
	private int Captime=5;                                 //times of collection
	private int X_,Y_;                                   //x,y coordinate
	private SQLiteDatabase Db;                           //database variable
	private static String DbName="WiCol_db";             //database name
	private static final String DATA_TB="dataOfWiCol";   //table name for data
	private static String M="WiCol";                     //indicate the place of error
	private List<ScanResult> listResult;                 //save the result of scanning 
	private Cursor cursor;                               //the cursor
	private ArrayList<Map<String,Object>> data;          //save key-value pair of data
	private ListView listview1;                          //ListView for data
	private ListView listview2;                          //ListView for fingerPrint
	private ArrayList<Item> filedata;                    //buffer file data when save
	private String TB_NAME;                              //Table name
	private String condition;                            //condition
	private static final String FPTB_NAME="fingerPrint"; //table name for fingerPrint
	private static String APTB_NAME="chosenAp";          //table name for AP
	private static String LOCATB_NAME="allLocation";     //table name for location
	private static String VIEW_NAME="ap_view";           //name of view to assist choose AP 
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_wi_col_);
		
//--Tab-------------------------------------------------------------------
		//get TabHost, initialize setup()
		TabHost tabs=(TabHost)findViewById(R.id.tabhost);
		tabs.setup();
		init();
		//add first tab
		TabHost.TabSpec spec1=tabs.newTabSpec("Tag1");
		spec1.setContent(R.id.DATA);              //add tab content
		spec1.setIndicator("DATA");               //indicator of this tab
		tabs.addTab(spec1);
		
		//add second tab
		TabHost.TabSpec spec2=tabs.newTabSpec("Tag2");
		spec2.setContent(R.id.Wifi_FingerPrint);           //add tab content
		spec2.setIndicator("WIFI_fINGERPRINT");                //indicator of this tab
		tabs.addTab(spec2);
		
		//set the tab to show at beginning
		tabs.setCurrentTab(0);
		
		//judge which tab is chosen
		tabs.setOnTabChangedListener(new OnTabChangeListener() { 
			public void onTabChanged(String tabId) {
				if(tabId.equals("Tag1")){
					whichTab=0;
				}else if(tabId.equals("Tag2")){
					whichTab=1;
				}else
					wrong();
			}	
		});
		
//----------------------------------------------------------------------------
	
		
		
//---for data-------------------------------------------------------------------
		//relative EditView with x_coordinate£¬y_coordinate
		et_x=(EditText)this.findViewById(R.id.xet1);
		et_y=(EditText)this.findViewById(R.id.yet1);
		
		//set button for collecting
		Button begincol=(Button)this.findViewById(R.id.colbtn);
		begincol.setOnClickListener(new OnClickListener(){
        	public void onClick(View v){
        		beginCollecting();
        	}
        });
		
		//set context menu for group TextView
		group_tv=(TextView)findViewById(R.id.grouptv1);
		registerForContextMenu(group_tv);
		
		//get ListView
		listview1=(ListView)findViewById(R.id.datalv);
//------------------------------------------------------------------------------
		
//---for finger_print-------------------------------------------------------------
		
		Button b1=(Button)this.findViewById(R.id.buildbtn);
		b1.setOnClickListener(new OnClickListener(){
        	public void onClick(View v){
        		build_fingerprint();
        	}
        }); 


		Button generateBtn = (Button) findViewById(R.id.generatebtn);

		final EditText row=(EditText)findViewById(R.id.line);
		final EditText tod=(EditText)findViewById(R.id.column);
		generateBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if((row.getText().toString().trim()).equals("")||(tod.getText().toString().trim()).equals("")){
					Toast.makeText(getApplicationContext(),"Must enter the room line and column",Toast.LENGTH_SHORT).show();
				}else{
					int xx,yy;
					xx=Integer.parseInt(row.getText().toString().trim());
					yy=Integer.parseInt(tod.getText().toString().trim());

					Intent intent = new Intent(WiCol_Activity.this,RoomLayout.class);
					intent.putExtra("row",xx);
					intent.putExtra("tod",yy);
					startActivity(intent);
				}

			}
		});

		//get ListView
		listview2=(ListView)findViewById(R.id.fingerprintlv);
//--------------------------------------------------------------------------------
		
		
	}

	
	
//---Menu---------------------------------------------------------------------------
	public boolean onCreateOptionsMenu(Menu menu){
		MenuInflater inflater=getMenuInflater();
		inflater.inflate(R.menu.wi_col_,menu);
		return true;
	}
	
	public boolean onOptionsItemSelected(MenuItem item){	
		switch(item.getItemId()){
		case R.id.Find_item:
			//show dialog
			FindDialog(WiCol_Activity.this);
			break;
		case R.id.Save_item:
			//show dialog
			SaveDialog(WiCol_Activity.this);
			break;	
		case R.id.Delete_item:
			//show dialog
			DeleteDialog(WiCol_Activity.this);
			break;
		default :
			break;
		}
		return super.onOptionsItemSelected(item);
	}
//-----------------------------------------------------------------------------
	
//---for data-------------------------------------------------------------------
	
//===collecting=================================================================
	//control the process of collecting
	private void beginCollecting(){
		Toast.makeText(this," collecting  ",Toast.LENGTH_SHORT).show();
		if((et_x.getText().toString().trim()).equals("")||(et_y.getText().toString().trim()).equals("")){
			xy_error();
		}else{
			getpara();          //1.get parameters
			dbbuild();          //2.prepare database
			collectCycle();     //3.collect data according group number
		}
	}
	
	private void getpara(){

		//get x,y coordinates
		X_=Integer.parseInt(et_x.getText().toString().trim());
		Y_=Integer.parseInt(et_y.getText().toString().trim());
	}
		
	//control the times of collecting
	private void collectCycle(){
		for(int i=Captime;i>0;i--){
			upDate();
		}
		insertdata("cut-off rule",0);
		Db.close();
		//clear the last value of x,y
		et_x.setText("");
		et_y.setText("");
	}
	
	//access the service of WiFi,open WLAN,scan the WiFi list,
	//and then save in listResult
	private void upDate(){
		WifiManager wifiManager;
		wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);  
        wifiManager.startScan();  
        listResult=null;
        listResult = wifiManager.getScanResults();
        //call upload
        upload();
	}
	
	//add data into listResult
	private void upload(){
		if(listResult!=null){ 
			for (ScanResult myScanResult:listResult) {    
				insertdata(myScanResult.BSSID,myScanResult.level);
			}
			insertdata("0",0);
		}else{
			Toast.makeText(this,"WIFI IS NOT OPEN!",Toast.LENGTH_SHORT).show();
		}
	}
	
	//add data into dataOfWiCol,
	//and check if success
	private void insertdata(String m,int r){
		ContentValues values=new ContentValues();
		values.put("X_", X_);
		values.put("Y_", Y_);
		values.put("mac", m);
		values.put("rss", r);
		long check1=Db.insert(DATA_TB, null, values);
		if(check1==-1)
			Log.i(M,"Inserting failure!");
		else
			Log.i(M,"Inserting success!" + check1);
	}
		
	//set context menu for group number
	public boolean onContextItemSelected(MenuItem item){
		switch(item.getItemId()){
		case R.id.Five_item:
			group_tv.setText(" Times:5  ");
			Captime=5;
			break;
		case R.id.Twenty_item:
			group_tv.setText(" Times:20  ");
			Captime=20;
			break;
		case R.id.Fifty_item:
			group_tv.setText(" Times:50  ");
			Captime=50;
			break;
		case R.id.Hundred_item:
			group_tv.setText(" Times:100  ");
			Captime=100;
			break;
		default:
			group_tv.setText(" Times:5  ");
			Captime=5;
			break;
		}
		return super.onContextItemSelected(item);
	}
	
	public void onCreateContextMenu(ContextMenu menu,View v,ContextMenuInfo menuInfo){
		MenuInflater inflater=getMenuInflater();
		inflater.inflate(R.menu.times, menu);
		super.onCreateContextMenu(menu, v, menuInfo);
	}
	
//=====================================================================================================
	
//------------------------------------------------------------------------------------------------------
	
//---for finger_print----------------------------------------------------------------------------------
	
	private void build_fingerprint(){
		Toast.makeText(this," building  ",Toast.LENGTH_LONG).show();
		
		dbbuild();                               //1.prepare database
		table_prepare();                         //2.prepare relative table
		choose_ap();                             //3.choose AP
	    FingerPrint fp=new FingerPrint();        
		fp.start(Db);                            //4.get WiFi fingerPrint
		Toast.makeText(this,"WIFI-FINGERPRINT MAKING SUCCESS£¡",Toast.LENGTH_LONG).show();
		Db.close();                             

	}
	
	
	//prepare relative table
	private void table_prepare(){
		//update location information
		Db.execSQL("drop table if exists " + LOCATB_NAME);
		Db.execSQL("create table if not exists " + LOCATB_NAME + "( " +
				"id_ integer primary key autoincrement,X_ integer,Y_ integer)");
		Db.execSQL("insert into " + LOCATB_NAME + "(X_,Y_) select distinct X_,Y_ from " + DATA_TB);
		//use view¡ª¡ª"ap_view" to compress data according to  different coordinates and MAC
		Db.execSQL("create view if not exists " + VIEW_NAME + 
				" as select X_,Y_,mac from " + DATA_TB + " group by X_,Y_,mac");
		//update AP table 
		Db.execSQL("drop table if exists " + APTB_NAME);
		Db.execSQL("create table if not exists " + APTB_NAME + 
				"( id_ integer primary key autoincrement,apmac varchar)");
	}
	private void init(){
		Stetho.initializeWithDefaults(this);
		new OkHttpClient.Builder()
				.addNetworkInterceptor(new StethoInterceptor())
				.build();
	}
	//AP selection
	private void choose_ap(){
		ArrayList<String> APall,APtempt;
		APall=new ArrayList<String>();         //used to save all AP been chosen(include "0" and "cut-off rule")
		APtempt=new ArrayList<String>();       //buffer AP
		String x,y;                            //buffer x,y coordinates
		Cursor cursor1,cursor2;                //cursor variable
		long check;                            //check if data insertion is success
		
		//this algorithm is to choose AP that all location can access to
		//1.determine the location coordinates
		//2.find all data about the location in view
		//3.update the set of AP according to the MAC owning by the location 
		//briefly speaking, if this location is the first one, then add all AP it has;
		//otherwise,delete those AP this location does not have in the set of AP.
		cursor1=get_cursor(LOCATB_NAME,null,null);
		cursor1.moveToFirst();
		while(!cursor1.isAfterLast()){
			x=cursor1.getString(1);
			y=cursor1.getString(2);
			condition="X_ =" + x + " and Y_ =" + y;
			cursor2=get_cursor(VIEW_NAME,condition,null);
			cursor2.moveToFirst();
			APtempt.clear();
			if(APall.size()==0){
				while(!cursor2.isAfterLast()){
					APall.add(cursor2.getString(2));
					cursor2.moveToNext();
				}
			}else{
				while(!cursor2.isAfterLast()){
					if(APall.contains(cursor2.getString(2))){
						APtempt.add(cursor2.getString(2));
					}
					cursor2.moveToNext();
				}
				APall.clear();
				for(String s:APtempt){
					APall.add(s);
				}
			}
			cursor2.close();
			cursor1.moveToNext();
		}
		cursor1.close();
		
		//check if choose AP except "0" and "cut-off rule" successfully
		if(APall.size()<=2){
			Toast.makeText(this,"AP CHOOSING FAILURE!",Toast.LENGTH_LONG).show();
			wrong();
		}
		//insert the valid AP into APTB_NAME
		for(String str:APall){
			if(str.equals("0"));
			else if(str.equals("cut-off rule"));
			else{
				ContentValues values=new ContentValues();
				values.put("apmac",str);
				Log.i("ap","" + str);
				check=Db.insert(APTB_NAME, null, values);
				if(check==-1)
					Log.i(M,"Inserting failure!");
				else
					Log.i(M,"Inserting success!" + check);
			}
		}
	}
	
	private Cursor get_cursor(String tablename, String where,String order){
		Cursor cur=Db.query(tablename,null,where,null,null,null,order);       //search by condition
		return cur;                                                           //return cursor
	}
	
	
//--------------------------------------------------------------------------------------------------
	
//---share functions and methods---------------------------------------------------------------------------------
	//find function
	private void findd(int xx,int yy){
		if(whichTab==0){	
			Toast.makeText(this," data-find  ",Toast.LENGTH_LONG).show();
			TB_NAME=DATA_TB;
		}else if(whichTab==1){
			Toast.makeText(this," finger-find  ",Toast.LENGTH_LONG).show();
			TB_NAME=FPTB_NAME;
		}else
			wrong();
		
		//search by x,y
		dbbuild();
		if(xx==-1||yy==-1){
			condition=null;
		}else
			condition="X_ =" + xx + " and Y_ =" + yy;     
		Table t=new Table();
		t.query(condition);  
		show();
	}
	
	//save function
	private void savee(String filename){
		if(whichTab==0){
			Toast.makeText(this," data-save  ",Toast.LENGTH_LONG).show();
			TB_NAME=DATA_TB;
		}else if(whichTab==1){
			Toast.makeText(this," finger-save  ",Toast.LENGTH_LONG).show();
			TB_NAME=FPTB_NAME;
		}else
			wrong();
		
		filedata=new ArrayList<Item>();
		dbbuild();
		Table t=new Table();
		t.build();
		t.query(condition); 
		filedata.clear();
		getData(true);
		Date now = new Date(); 
		DateFormat day = DateFormat.getDateTimeInstance();  
		String str = day.format(now); 
		//check if file writing is OK
		String state=Environment.getExternalStorageState();
		if(Environment.MEDIA_MOUNTED.equals(state)){
			Toast.makeText(this," WR ",Toast.LENGTH_SHORT).show();           // both write and read is OK 
		}else if(Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)){
			Toast.makeText(this," R ",Toast.LENGTH_SHORT).show();            //read is OK
		}else{
			Toast.makeText(this," WR_ERROR ",Toast.LENGTH_SHORT).show();     //write and read error
		}
		//operation to save the file
		try{
			Toast.makeText(this," " + filedata.size(),Toast.LENGTH_SHORT).show();
			FileOutputStream outStream = new FileOutputStream(Environment.getExternalStorageDirectory().getPath() +
					"/"+ filename + ".txt",true);
			OutputStreamWriter writer = new OutputStreamWriter(outStream,"gb2312");
			writer.write(" Creating Time£º " + str + "\n");
			writer.write(" ID  X  Y  MAC  RSS " + "\n");
			for(int i=0;i<filedata.size();i++){
				Item item=filedata.get(i);
				//writer.flush();
				writer.write(item.id + " " +           //write data into file
						item.x + " " + 
						item.y + " " + 
						item.mac + " " +
						item.rss + "\n");  
				Log.i("item " + i,item.id + " " +      //show in log
						item.x + " " + 
						item.y + " " + 
						item.mac + " " +
						item.rss);
			}              
			writer.close();     
			outStream.close();                         //finish writing
			Toast.makeText(this,"Save Success!",Toast.LENGTH_LONG).show();
		}catch(IOException ex){
			Toast.makeText(this,"Save Failure!",Toast.LENGTH_LONG).show();
			ex.printStackTrace();
		}
		
	}
	
	//delete function
	private void deletee(int xx,int yy){
		if(whichTab==0){
			Toast.makeText(this," data-delete  ",Toast.LENGTH_LONG).show();
			TB_NAME=DATA_TB;
		}else if(whichTab==1){
			Toast.makeText(this," finger-delete  ",Toast.LENGTH_LONG).show();
			TB_NAME=FPTB_NAME;
		}else
			wrong();
		
		dbbuild();             
		if(xx==-1||yy==-1){
			condition=null;
		}else
			condition="X_ =" + xx + " and Y_ =" + yy;     
		Table t=new Table();
		t.build();
		t.delete(condition);           
		Db.close();        
	}
	
	//set find dialog content
	private void FindDialog(Context context){
		LayoutInflater inflater = LayoutInflater.from(this);
		final View v1 = inflater.inflate(
				R.layout.find_dialog, null);
		final EditText xet=(EditText)v1.findViewById(R.id.xxet);
		final EditText yet=(EditText)v1.findViewById(R.id.yyet);
		TextView msg=(TextView)v1.findViewById(R.id.msg1);
		msg.setText("Find by the coordinates");
		final AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setCancelable(false);
		builder.setTitle("WiCol");
	//	builder.setIcon(R.drawable.icon);
		builder.setView(v1);
		builder.setPositiveButton("Search",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						if((xet.getText().toString().trim()).equals("")||(yet.getText().toString().trim()).equals("")){
							xy_error();
						}else{
							int xx,yy;
							xx=Integer.parseInt(xet.getText().toString().trim());
							yy=Integer.parseInt(yet.getText().toString().trim());
							findd(xx,yy);
						}
				}
		});
		builder.setNeutralButton("Search all",
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				findd(-1,-1);
			}
		});
		builder.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
					}
		});
		builder.show();
	}
	
	//set save dialog content
	private void SaveDialog(Context context){
		LayoutInflater inflater = LayoutInflater.from(this);
		final View v1 = inflater.inflate(
				R.layout.save_dialog, null);
		final EditText file_et=(EditText)v1.findViewById(R.id.fileet);
		final AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setCancelable(false);
		builder.setTitle("WiCol");
	//	builder.setIcon(R.drawable.icon);
		builder.setView(v1);
		builder.setPositiveButton("Save",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String filename=file_et.getText().toString().trim();
						if(filename.equals(""))
							filename_error();						
						else
							savee(filename);
					}
				});
		builder.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
					}
				});
		builder.show();
	}
	
	//set delete dialog content
	private void DeleteDialog(Context context){
		LayoutInflater inflater = LayoutInflater.from(this);
		final View v1 = inflater.inflate(
				R.layout.find_dialog, null);
		final EditText xet=(EditText)v1.findViewById(R.id.xxet);
		final EditText yet=(EditText)v1.findViewById(R.id.yyet);
		TextView msg=(TextView)v1.findViewById(R.id.msg1);
		msg.setText("Delete by the coordinates");
		final AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setCancelable(false);
		builder.setTitle("WiCol");
	//	builder.setIcon(R.drawable.icon);
		builder.setView(v1);
		builder.setPositiveButton("Delete",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						if((xet.getText().toString().trim()).equals("")||(yet.getText().toString().trim()).equals("")){
							xy_error();
						}else{
							int xx,yy;
							xx=Integer.parseInt(xet.getText().toString().trim());
							yy=Integer.parseInt(yet.getText().toString().trim());
							deletee(xx,yy);
						}
					}
				});
		builder.setNeutralButton("Delete all",
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				deletee(-1,-1);
			}
		});
		builder.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
					}
		});
		builder.show();
	}
	
	//show ListView
	private void show(){
		getData(false);
		SimpleAdapter listAdapter=new SimpleAdapter(this,data,R.layout.data_lv,
				new String[]{"id","X_","Y_","mac","rss"},
				new int[]{R.id.idtv,R.id.x_tv,R.id.y_tv,R.id.mac_tv,R.id.rss_tv});
		if(whichTab==0){
			listview1.setAdapter(listAdapter);
		}else if(whichTab==1){
			listview2.setAdapter(listAdapter);
		}else
			wrong();
	}
	
	//get data from the table
	private void getData(boolean save_show){
		String id,x,y,mac1,rss1;
		Map<String,Object> item;
		Item items;
		data=new ArrayList<Map<String,Object>>();
		cursor.moveToFirst();
		while(!cursor.isAfterLast()){
			id=cursor.getString(0);
			x=cursor.getString(1);
			y=cursor.getString(2);
			mac1=cursor.getString(3);
			rss1=cursor.getString(4);
			if(save_show){
				items=new Item();
				items.id=id;
				items.x=x;
				items.y=y;
				items.mac=mac1;
				items.rss=rss1;
				filedata.add(items);
				items=null;
			}else{
				item=new HashMap<String,Object>();
				item.put("id", id);
				item.put("X_", x);
				item.put("Y_", y);
				item.put("mac", mac1);
				item.put("rss", rss1);
				data.add(item);
				item=null;
			}	
			cursor.moveToNext();
		}
		cursor.close();
		Db.close();
	}
	

	//prepare the database
	private void dbbuild(){
		DB_helper dbHelper=new DB_helper(this,DbName,null,1);
		Db=dbHelper.getWritableDatabase();
		Db.execSQL("create table if not exists "     //create the table if not exist
				+ DATA_TB 
				+ "( id_ integer primary key autoincrement, X_ integer, Y_ integer, mac varchar,rss integer)");
		Db.execSQL("create table if not exists "     //create the table if not exist
				+ FPTB_NAME 
				+ "( id_ integer primary key autoincrement, X_ integer, Y_ integer, mac varchar,rss integer)");
	}
	
	//inner class to facilitate table operation
	class Table{
		//new
		private void build(){
			Db.execSQL("create table if not exists "            //create table if not exist
				+ TB_NAME 
				+ "( id_ integer primary key autoincrement, X_ integer, Y_ integer, mac varchar,rss integer)");
		}
		//delete
		private void delete(String where){                             
			Db.delete(TB_NAME,where,null);                     //delete data by condition
		}
		//query
		private void query(String where){
			cursor=Db.query(TB_NAME,null,where,null,null,null,"id_ ASC");     //search by condition
		}
		
	}
	
	//table item
	class Item{
		private String id,x,y,mac,rss;
	}
	
	
	
	
	
//---------------------------------------------------------------------------------------------------	
	
	//if x or y is not input
	private void xy_error(){
		Toast.makeText(this,"PLEASE INPUT X AND Y!",Toast.LENGTH_SHORT).show();
	}
	
	//if filename is not input
	private void filename_error(){
		Toast.makeText(this,"PLEASE INPUT FILENAME!",Toast.LENGTH_SHORT).show();
	}
	
	//if something wrong or exit
	public void wrong(){
		finish();
	}
}
