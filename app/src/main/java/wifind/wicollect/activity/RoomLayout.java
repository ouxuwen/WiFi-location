package wifind.wicollect.activity;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import wifind.wicollect.R;
import wifind.wicollect.sqlite.DB_helper;

import static wifind.wicollect.activity.RoomDataBase.OriginalMap;

/**
 * Created by Mo on 2018/2/24.
 */

public class RoomLayout extends Activity {
    private  int row = 0;
    private int tod = 0;
    private String[] roomArray;
    private GridView grid_main;
    private MyGridAdapter adapter;
    private int currentPostion = 10000;
    private Timer timer;
    private SQLiteDatabase Db;                           //database variable
    private static String DbName="WiCol_db";             //database name
    private static final String TestTable="testOfWiCol";   //table name for test
    private List<ScanResult> listResult;                 //save the result of scanning
    private static String M="TestCol";                     //indicate the place of error
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.room_layout);
        Intent intent = getIntent();
        row = intent.getIntExtra("row",3);
        tod = intent.getIntExtra("tod",1);
        roomArray = new String[row*tod];
        int dj = 0;
        for(int i = 1;i<=row;i++){
            for(int j = 1;j<=tod;j++){
                roomArray[dj] = i + "X" + j;
                dj++;
            }
        }

        grid_main = (GridView) findViewById(R.id.gridView2);
        grid_main.setNumColumns(tod);
        adapter = new MyGridAdapter();
        grid_main.setAdapter(adapter);



        Button beginBtn = (Button) findViewById(R.id.beginBtn);
        beginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                begin();
            }
        });

        Button stopBtn = (Button) findViewById(R.id.stopBtn);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopC();
            }
        });

    }

    protected class MyGridAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return row*tod;
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // TODO Auto-generated method stub
            View view = View.inflate(RoomLayout.this, R.layout.item_view, null);
            TextView title = (TextView) view.findViewById(R.id.textView1);
            title.setText(roomArray[position]);

            if(position == currentPostion ){
                title.setBackgroundColor(android.graphics.Color.BLUE);
                title.setTextColor(Color.WHITE);
            }


            return view;
        }
    }

    public void begin(){
        findPoint();
//        final Handler handler = new Handler(){
//            @Override
//            public void handleMessage(Message msg) {
//                findPoint();
//
//
//
//            }
//        };
//
//        timer = new Timer();
//
//        timer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//
//                Message msg = Message.obtain();
//                msg.obj = currentPostion;
//                handler.sendMessage(msg);
//            }
//        },0,10000);
    }

    public void stopC(){
        if(null != timer){
            timer.purge();
            timer.cancel();
            timer = null;
        }
    }


    //prepare the database
    private void dbbuild(){
        DB_helper dbHelper=new DB_helper(this,DbName,null,1);
        Db=dbHelper.getWritableDatabase();
        Db.execSQL("drop table if  exists "+TestTable);
                Db.execSQL("create table if not exists "     //create the table if not exist
                + TestTable
                + "( id_ integer primary key autoincrement,  mac varchar,rss integer)");

    }

    //control the times of collecting
    private void collectCycle(){
        for(int i=5;i>0;i--){
            upDate();
        }



    }

    private void beginCollecting(){
        Toast.makeText(this," Begin location  ",Toast.LENGTH_SHORT).show();
            dbbuild();          //2.prepare database

            collectCycle();     //3.collect data according group number

    }

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

        }else{
            Toast.makeText(this,"WIFI IS NOT OPEN!",Toast.LENGTH_SHORT).show();
        }
    }

    //add data into dataOfWiCol,
    //and check if success
    private void insertdata(String m,int r){
        ContentValues values=new ContentValues();

        values.put("mac", m);
        values.put("rss", r);
        long check1=Db.insert(TestTable, null, values);
        if(check1==-1)
            Log.i(M,"Inserting failure!");
        else
            Log.i(M,"Inserting success!" + check1);
    }

    //clear the  test table


    private Map<String,Double> getList(){
        Map<String,Double> resultList = new  HashMap<String,Double>();
        String ap,rss;
        String VIEW_NAME="test_view";                //name of view to assist producing fingerPrint
        Db.execSQL("drop view if exists " + VIEW_NAME);
        Db.execSQL("create view if not exists " + VIEW_NAME
                + " as select mac,avg(rss) from " + TestTable + " group by mac");    //average of rss
        Cursor cursor =Db.query(VIEW_NAME,null,null,null,null,null,null );
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            ap=cursor.getString(0);
            rss=cursor.getString(1);
            resultList.put(ap, Double.valueOf(rss));
            cursor.moveToNext();
        }
        cursor.close();
        Db.close();
        return resultList;
    }

    //compare every point ,find the closer point
    private String comparePoint(Map<String,Double> nowList,Map<String,Map<String,Double>> originList){
        String resultStr;
        List<ResultObject> results = new ArrayList<ResultObject>();
        for (String key : originList.keySet()) {
            int i = 0;
            Double avg = 0.0;
            Map<String,Double> item = originList.get(key);
            for(String key2:item.keySet()){
                if(nowList.containsKey(key2)){
                    i++;
                    avg = avg + Math.pow(Math.pow(nowList.get(key2) - item.get(key2),2), 1/2)  ;
                }
            }
            if(i==0){
              //  results.add(new ResultObject(key,10000000000.00));
            }else{
                results.add(new ResultObject(key,avg/i));
            }
        }
        Double min =10000.0;

        resultStr = "l_1_1";
        if(results.size()>0){
            min = results.get(0).similarity ;
            resultStr = results.get(0).coordinate;
        }
        for(int k = 0;k<results.size();k++){
            if(results.get(k).similarity < min){
                resultStr = results.get(k).coordinate;
                min = results.get(k).similarity;

            }
        }
        return resultStr;

    }
    public class ResultObject{
        public String coordinate;
        public Double similarity;

        public ResultObject(String coordinate,Double similarity){
            this.coordinate = coordinate;
            this.similarity = similarity;
        }


    }

    //find the point
    public void findPoint(){
        if(null == OriginalMap){
            Toast.makeText(getApplicationContext(),"Not Builded",Toast.LENGTH_SHORT).show();
            return;
        }
        beginCollecting();
        Map<String,Double> resultList = getList();
        int dj = resultList.size();


        String result = comparePoint(resultList,OriginalMap);
        if("".equals(result)){

            Toast.makeText(getApplicationContext(),"NOT the sam room",Toast.LENGTH_SHORT).show();
            return;
        }
        int _x = Integer.parseInt(result.split("_")[1],10);
        int _y = Integer.parseInt(result.split("_")[2],10);

        Toast.makeText(getApplicationContext(),"Now your postion is "+_x+" rows and "+_y+" columns",Toast.LENGTH_LONG).show();

        currentPostion = (_x -1)* row+_y-1;
        grid_main.setAdapter(adapter);
    }
}
