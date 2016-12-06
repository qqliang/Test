package com.database.pager;

import com.database.global.ColumnConstraint;
import com.database.global.DataType;
import com.database.global.SpaceAllocation;
import com.database.global.Utils;
import com.sun.org.apache.xerces.internal.impl.dv.DatatypeException;
import com.sun.org.apache.xerces.internal.impl.xs.util.ShortListImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by zoe on 2016/12/5.
 */
public class Record {
    private List<Column> columns;
    private int size ;

    public Record(List<Column> columns) {
        this.columns = columns;
        this.size = calculateSize();
    }

    public Record() {
    }

    public List<Column> getColumns() {
        return columns;
    }

    /**
     *  对一条记录中的行信息进行设置
     * @param columns   要设置的行信息列表
     */
    public void setColumns(List<Column> columns) {
        this.columns = columns;
        this.size = calculateSize();
    }

    /**
     * 对一条记录中的行信息进行设置
     * @param recordStr     一条记录的字符串，每个字段以逗号分隔。格式为：xxx,yyy,zzz......
     * @param type          与recordStr每个字段对应的类型
     * @param constraints   与每个字段对应的字段约束，可以为NULL
     */
    public void setColumns(String recordStr, List<Byte> type, List<Byte> constraints) {
        String[] cols = recordStr.split(",");

        assert(cols.length == type.size());

        if(constraints == null || constraints.size() == 0) {
            constraints = new ArrayList<Byte>(cols.length);
            Collections.fill(constraints, ColumnConstraint.NONE);
        }else if(constraints.size() < cols.length){
            List<Byte> newConstraints = new ArrayList<Byte>(cols.length);
            for(int i = 0; i < cols.length; i++){
                if(i < constraints.size())
                    newConstraints.set(i,constraints.get(i));
                else
                    newConstraints.set(i,ColumnConstraint.NONE);
            }
        }

        if(this.columns != null && this.columns.size()!=0)
            this.columns.clear();

        for(int i =0; i<cols.length; i++){
            Column column = new Column();
            column.setName(cols[i]);
            column.setType(type.get(i));
            column.setConstraint(ColumnConstraint.NONE);

            this.columns.add(column);
        }

        this.size = calculateSize();
    }

    /**
     * 计算一条记录所占空间总大小
     * @return
     */
    public int calculateSize(){
        if(this.columns == null || this.columns.size() == 0)
            return 0;

        int size = getColNum() + SpaceAllocation.RECORD_HEADER ;

        for(int i = 0; i < getColNum(); i++){
            byte type = this.columns.get(i).getType();
            switch (type){
               case DataType.INTEGER :
                   size += 4;
                   break;
               case DataType.LONG :
                   size += 8;
                   break;
               case DataType.TEXT:
                   size += 50;
                   break;
                case DataType.SMALL_INT:
                    size += 2;
                    break;
                case DataType.TINY_INT:
                    size += 1;
                    break;
            }
        }
        return size;
    }

    public int getSize(){
        return  this.size;
    }
    public int getColNum(){
        return this.columns.size();
    }

    public byte[] getBytes(String record){
        if(record == null || record.isEmpty())
            return null;
        return getBytes(Arrays.asList(record.split(",")));
    }
    public byte[] getBytes(List<String> record)
    {
        if(columns == null || columns.size() == 0)
            return null;

        int colNum = getColNum();
        byte[] data = new byte[this.size];
        int headerSize = SpaceAllocation.RECORD_HEADER + colNum;

        Utils.fillInt(headerSize, data, 0);
        for(int i = 0 ; i < colNum; i++){
            data[SpaceAllocation.RECORD_HEADER+i] = this.columns.get(i).getType();
        }
        int offset = 0 ;

        for(int i = 0 ; i < colNum; i++){
            Column col = this.columns.get(i);
            switch (col.getType()){
                case DataType.INTEGER:
                    int intValue = Integer.parseInt(record.get(i));
                    Utils.fillInt(intValue, data, headerSize+offset);
                    offset += 4;
                    break;
                case DataType.LONG:
                    long longValue = Long.parseLong(record.get(i));
                    Utils.fillLong(longValue, data, headerSize+offset);
                    offset += 8;
                    break;
                case DataType.SMALL_INT:
                    short shortValue = Short.parseShort(record.get(i));
                    Utils.fillInt(shortValue, data, headerSize+offset);
                    offset += 2;
                    break;
                case DataType.TINY_INT:
                    byte byteValue = Byte.parseByte(record.get(i));
                    data[headerSize + offset] = byteValue;
                    offset += 1;
                    break;
                case DataType.TEXT:
                    Utils.fillString(record.get(i), data, headerSize+offset);
                    offset += 50;
                    break;
            }
        }
        return data;
    }
}