package com.database.main;

import com.database.global.ColumnConstraint;
import com.database.global.DataType;
import com.database.global.Database;
import com.database.global.PageType;
import com.database.pager.Column;
import com.database.pager.Pager;
import com.database.pager.TableSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by zoe on 2016/12/5.
 */
public class TestPager {
    public static void main(String[] args){
//        testRead();
//        testReadByRowid();
//        testFlush();
        testLoad();
    }
    public static void testFlush(){
        Database database = new Database();
        database.openDB("test2");
        Pager pager = new Pager(database);
        TableSchema record = getRecord();
        int rowid = 0;
        pager.writeData(1, record.getBytes(++rowid, "1,zhouyu,22"));
        pager.writeData(1, record.getBytes(++rowid,"2,lqq,22"));
        pager.writeData(1, record.getBytes(++rowid,"3,hh,26"));

        pager.writeData(1, record.getBytes(++rowid,"4,dxr,27"));
        pager.writeData(1, record.getBytes(++rowid,"5,yyc,27"));
        pager.writeData(1, record.getBytes(++rowid,"6,whw,22"));

        pager.flush();

    }
    public static void testLoad()
    {
        Database database = new Database();
        database.openDB("test2");
        Pager pager = new Pager(database);
        pager.loadDB();

        List<Map.Entry<Integer, String>> list = pager.readRecord(1);
        System.out.println(list);
    }
    public static void testRead(){
        Database database = new Database();
        database.openDB("test");
        Pager pager = new Pager(database);
        TableSchema record = getRecord();
        int rowid = 0;
        pager.writeData(1,record.getBytes(rowid++,"1,zhouyu,22"));
        pager.writeData(1,record.getBytes(rowid++,"2,lqq,22"));
        pager.writeData(1,record.getBytes(rowid++,"3,hh,26"));

        List<Map.Entry<Integer, String>> list = pager.readRecord(1);
        System.out.println(list.toString());
    }
    public static void testReadByRowid(){
        Database database = new Database();
        database.openDB("test");
        Pager pager = new Pager(database);
        TableSchema record = getRecord();
        pager.getPages()[1].setPageType(PageType.TABLE_LEAF);
        int rowid = 0;
        pager.writeData(1,record.getBytes(rowid++,"1,zhouyu,22"));
        pager.writeData(1,record.getBytes(rowid++,"2,lqq,22"));
        pager.writeData(1,record.getBytes(rowid++,"3,hh,26"));

        String row = pager.readDataByRowid(1,2).getValue();
        System.out.println("row:"+row);
    }
    public static TableSchema getRecord() {
        TableSchema record = new TableSchema();
        List<Column> cols = new ArrayList<Column>();
        Column idCol =  new Column();
        idCol.setName("id");
        idCol.setType(DataType.INTEGER);
        idCol.setConstraint(ColumnConstraint.NONE);

        Column nameCol =  new Column();
        nameCol.setName("name");
        nameCol.setType(DataType.TEXT);
        nameCol.setConstraint(ColumnConstraint.NONE);

        Column ageCol =  new Column();
        ageCol.setName("age");
        ageCol.setType(DataType.TINY_INT);
        ageCol.setConstraint(ColumnConstraint.NONE);

        cols.add(idCol);
        cols.add(nameCol);
        cols.add(ageCol);
         record.setColumns(cols);
        return record;
    }
}
