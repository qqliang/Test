package com.database.global;

import com.database.myBplusTree.BplusNode;
import com.database.myBplusTree.BplusTree;
import com.database.pager.*;
import com.database.parse.CRUD;
import com.database.parse.parser;
import com.database.queryExecute.Execute;
import com.sun.xml.internal.ws.api.wsdl.parser.XMLEntityResolver;
import com.sun.xml.internal.ws.dump.LoggingDumpTube;

import java.io.File;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;

public class Database {

	private static String path = "D:/db";				//根目录
	private String dbName; 								//数据库名称
	private File dbFile;								//数据库文件
	private int stat; 									//数据库状态，打开为1，关闭为0
	private Pager pager; 								//pager对象
	private int dbSize;									//数据库大小
	private Page page1;									//数据库第1页

	private Map<String,BplusTree> tableTreeMap;			//表与B+树映射列表
	private int tableCount;								//数据库中表的个数

	//构造函数
	public Database(){
		this.pager = new Pager(this);
		this.tableTreeMap = new HashMap<>();
	}

	/** 设置和获取数据库名称 */
	public void setDBName(String dbName) {
		this.dbName = dbName;
	}
	public String getDBName() {
		return dbName;
	}

	/** 设置和获取数据库状态 */
	public int getStat() {
		return stat;
	}
	public void setStat(int stat) {
		this.stat = stat;
	}

	/** 设置和获取数据库文件 */
	public String getDBFile() {
		return dbFile.getAbsolutePath();
	}
	public void setDBFile(File dbFile) {
		this.dbFile = dbFile;
	}

	/** 设置和获取数据库名字 */
	public Pager getPager() {
		return pager;
	}
	public void setPager(Pager pager) {
		this.pager = pager;
	}

	/** 设置和获取数据库名字 */
	public int getDbSize() {
		File file = new File(getDBFile());
		if(file.exists() && file.isFile()){
			this.dbSize = (int)file.getTotalSpace()%SpaceAllocation.PAGE_SIZE;
		}
		return dbSize;
	}
	public void setDbSize(int dbSize) {
		this.dbSize = dbSize;
	}

	/** 设置和获取数据库名字 */
	public Page getPage1() {
		page1 = pager.aquirePage(1);
		return page1;
	}
	public void setPage1(Page page1){
		this.page1 = page1;
		pager.flush();
	}

	/** 向表树映射中添加映射关系 */
	public void addTableTree(String tableName,String sql, BplusTree tree){
		tableTreeMap.put(tableName, tree);

		TableSchema schema = TableSchema.getTreeSchema();
		Integer rootPgno = tree.getRoot().page.getPgno();
		String str = tableName+","+sql.replace(",","#");
		Entry<Integer,byte[]> entry = new SimpleEntry<Integer, byte[]>(
				rootPgno,schema.getBytes(rootPgno,str));
		tableCount++;

		/* 向page1中追加映射关系：B+树根页号 -> 表名，表约束 */
		page1 = pager.aquirePage(1);
		pager.appendData(page1,entry);
		page1.setTableCount(tableCount);

		pager.updateHeader(page1);
		pager.updateHeader(tree.getRoot().page);

		pager.flush();

	}
	/** 根据表名获取B+树 */
	public BplusTree getTableTreeByName (String tableName){
		return (BplusTree)tableTreeMap.get(tableName);
	}

	/**
	 * 根据数据库名称，打开数据库
	 * @param dbName
	 * @return 打开成功为1，否则为0
	 */
	public int openDB(String dbName){
		dbFile = new File(path + "/" + dbName);
		if (!dbFile.exists()) {
			return 0;//打开不成功
		}else{
			this.dbSize = (int)(dbFile.getTotalSpace()%SpaceAllocation.PAGE_SIZE);
//			this.pager.setMxPgno(this.dbSize <= 0 ? 0: this.dbSize);
			this.dbName = dbName;
			setStat(1);

			/* 获取page1中的表和树的映射关系 */
			page1 = pager.aquirePage(1);
			this.pager.setMxPgno(Utils.loadIntFromBytes(page1.getData(), Position.MAX_PGNO_IN_FIRST_PAGE));
			List<Entry<Integer,String>> entryList = pager.readRecord(1);
			if(entryList != null)
			{
				for(int i=0;i<entryList.size();i++){
					Page page = pager.aquirePage(entryList.get(i).getKey());
					BplusNode root = new BplusNode(pager,page);
					BplusNode head = new BplusNode(pager,pager.aquirePage(page.getHead()));
					BplusTree tree = new BplusTree(page.getOrder(),this,root,head);
					tableTreeMap.put(entryList.get(i).getValue().split(",")[0].trim(),tree);
				}
			}

			/* 获取表计数 */
			this.tableCount = page1.getTableCount();
			return 1;//打开成功
		}
	}



	/**
	 * sql语句的执行
	 * @param sql
	 */
	public void exeSQL(String sql){
		String result[] = parser.parser(sql);
		if(result[0] != null )
		{
			Execute execute = new Execute(this,pager);
			execute.queryDo(result,sql);
		}
	}
}
