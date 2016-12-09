package com.database.pager;

import java.io.*;
import java.util.*;
import com.database.global.*;


public class Pager {
	Database database;
	File journal;
	PCache pCache;
	Page[] pages;
	int pageNum;

	private List<Page> freeList;
	private int mxPgno;

	public Pager(Database database) {
		this.database = database;
//		this.journal = new File(this.database.getDBName()+"-journal");
		this.pCache = null;
		this.pageNum = 1000;
		initPages();
		this.freeList = new ArrayList<>();
		this.freeList.addAll(Arrays.asList(this.pages));
	}

	public Page[] getPages() {
		return pages;
	}

	public void setPages(Page[] pages) {
		this.pages = pages;
	}

	public int getPageNum() {
		return pageNum;
	}

	public void setPageNum(int pageNum) {
		this.pageNum = pageNum;
	}

	public void initPages(){
		this.pages = new Page[this.pageNum];
		for(int i =0 ;i <this.pages.length; i++){
			Page page = new Page();
			page.setData(new byte[SpaceAllocation.PAGE_SIZE]);
			page.setPgno(i);
			page.setOverflowPgno(0);
			page.setSectorSize(SpaceAllocation.SECTOR_SIZE);
			page.setSize(SpaceAllocation.PAGE_SIZE);
			page.setOffset(SpaceAllocation.PAGE_SIZE);
			pages[i] = page;
		}
	}
	public void writeRootPage(Page page){
		File dbFile = new File(database.getDBFile());
		byte[] rootPageData = new byte[SpaceAllocation.PAGE_SIZE];
		FileOutputStream fos = null;
		try{
			fos = new FileOutputStream(dbFile);
			fos.write(rootPageData,0,SpaceAllocation.PAGE_SIZE);
		}catch (FileNotFoundException e){
			e.printStackTrace();
		}catch (IOException e){
			e.printStackTrace();
		}
	}

	/**
	 * 像指定页面中写入数据，即刷新整个页面数据
	 * @param pgno 要写入数据的页号
	 * @param data	要写入的数据，entry中的byte[]可以通过Record的getBytes方法可以简单得到
	 */
	public void writeData(int pgno, List<Map.Entry<Integer, byte[]>> data){
		Page page = this.pages[pgno];
		page.fillData(data);
	}

	/**
	 * 像指定页面中追加数据(会有错误！)
	 * @param pgno 要写入数据的页号
	 * @param data	要写入的数据，可以通过Record的getBytes方法可以简单得到
	 */
	public void appendData(int pgno, byte[] data){
		Page page = this.pages[pgno];
		page.appendData(data);
	}
	/**
	 * 读取指定页面中的数据
	 * @param pgno 要写入数据的页号
	 * @return 指定页面的数据
	 */
	public byte[] readData(int pgno){
		if(pgno <= 0 )
			return null;

		return this.pages[pgno].getData();
	}
	/**
	 * 读取指定页面中的数据
	 * @param pgno 要读取的页号
	 * @param rowid 要读取的rowid
	 * @return 指定页面的数据
	 */
	public Map.Entry<Integer,String>  readDataByRowid(int pgno, int rowid){
		if(pgno <= 0 )
			return null;
		Map.Entry<Integer,String> entry = null;			//返回的结果
		Page page = this.pages[pgno];
		byte[] data = page.getData();
		if(page.getPageType() == PageType.TABLE_LEAF){
			int offset = page.getOffset();
			int szHdr = Utils.loadIntFromBytes(data, offset+4);
			int skip = szHdr;
			int[] types = new int[szHdr - SpaceAllocation.RECORD_HEADER];
			for(int col = 0 ; col< szHdr - SpaceAllocation.RECORD_HEADER; col++){
				switch (data[offset + SpaceAllocation.RECORD_HEADER + col]){
					case DataType.INTEGER:
						skip += 4;
						types[col] = DataType.INTEGER;
						break;
					case DataType.LONG:
						skip += 8;
						types[col] = DataType.LONG;
						break;
					case DataType.TINY_INT:
						skip += 1;
						types[col] = DataType.TINY_INT;
						break;
					case DataType.SMALL_INT:
						skip += 2;
						types[col] = DataType.SMALL_INT;
						break;
					case DataType.TEXT:
						skip += 50;
						types[col] = DataType.TEXT;
						break;
				}
			}

			for(; offset < page.getSize(); offset += skip){
				if(Utils.loadIntFromBytes(data, offset) == rowid){
					List<String> cols = null;
					entry = loadEntryFromBytes(data, szHdr, types, offset);
					break;
				}
			}
			return entry;
		}else{
			return entry;
		}
	}
	/**
	 * 读取指定页面中的数据返回记录
	 * @param pgno 要写入数据的页号
	 * @return 指定页面的记录的Map表示 rowid，记录值
	 */
	public List<Map.Entry<Integer, String>> readRecord(int pgno){
		if(pgno <= 0 )
			return null;
		List<String> result = new ArrayList<String>();
		Page page = this.pages[pgno];
		int offset = page.getOffset();
		if(offset == page.getSize() || offset <= SpaceAllocation.PAGE_HEADER_SIZE)
			return null;
		byte[] data = page.getData();
		int hdrSz = Utils.loadIntFromBytes(data, offset + 4);
		int[] types = new int[hdrSz - SpaceAllocation.RECORD_HEADER];
		int dataSize = 0;
		for(int i =0; i < hdrSz - SpaceAllocation.RECORD_HEADER; i++){
			byte type = data[offset+SpaceAllocation.RECORD_HEADER + i];
			switch(type){
				case DataType.INTEGER:
					dataSize += 4;
					types[i] = DataType.INTEGER;
					break;
				case DataType.SMALL_INT:
					dataSize += 2;
					types[i] = DataType.SMALL_INT;
					break;
				case DataType.TINY_INT:
					dataSize += 1;
					types[i] = DataType.TINY_INT;
					break;
				case DataType.TEXT:
					dataSize += 50;
					types[i] = DataType.TEXT;
					break;
				case DataType.LONG:
					dataSize += 8;
					types[i] = DataType.LONG;
					break;
			}
		}
		List<Map.Entry<Integer, String>> list = new ArrayList<Map.Entry<Integer, String>>();

		for(int index = offset; index < page.getSize(); ){
			Map.Entry<Integer, String> entry = loadEntryFromBytes(data, hdrSz, types, index);
			list.add(entry);
			index += hdrSz + dataSize;
		}

		return list;
	}

	public String colsToRow(List<String> cols){
		StringBuilder row = new StringBuilder();
		for(String col : cols){
			row.append(col+",");
		}
		return row.substring(0,row.length()-1);
	}
	/**
	 *
	 * @param data		数据加载的地方
	 * @param types
	 * @param hdrSz
	 * @param start		加载起始位置
	 * @return 一条记录对应的Entry
	 */
	public Map.Entry<Integer, String> loadEntryFromBytes(byte[] data, int hdrSz, int[] types, int start){
		Map<Integer, String> entry = new HashMap<Integer, String>();
		List<String> cols = new ArrayList<String>();
		int colNum = types.length;
		int rowid = Utils.loadIntFromBytes(data, start);
		start += hdrSz;
		for (int i = 0; i < colNum; i++) {
			switch (types[i]) {
				case DataType.INTEGER:
					cols.add(new Integer(Utils.loadIntFromBytes(data, start)).toString());
					start += 4;
					break;
				case DataType.SMALL_INT:
					cols.add(new Short(Utils.loadShortFromBytes(data, start)).toString());
					start += 2;
					break;
				case DataType.TINY_INT:
					cols.add(new Byte(data[start]).toString());
					start += 1;
					break;
				case DataType.TEXT:
					cols.add(Utils.loadStrFromBytes(data, start, 50));
					start += 50;
					break;
				case DataType.LONG:
					cols.add(new Long(Utils.loadLongFromBytes(data, start)).toString());
					start += 8;
					break;
			}
		}

		entry.put(rowid, colsToRow(cols));
		return entry.entrySet().iterator().next();
	}

	/**
	 * 刷新页面，写磁盘
	 */
	public void flush(){
		RandomAccessFile raf = null;
		try{
			raf = new RandomAccessFile(this.database.getDBFile(),"rw");
			raf.seek(0);
			for(int i =0; i<this.pageNum;i++)
			{
				Page page = this.pages[i];
//				page.updateData();
				raf.write(page.getData());
			}

		}catch (IOException e){
			e.printStackTrace();
		}finally {
			try{
				if(raf != null) raf.close();
			}catch (IOException e){
				e.printStackTrace();
			}

		}
	}

	/**
	 *
	 * @param pgno 页号
	 * @return
	 */
	public Page loadPage(int pgno){
		if(pgno <= 0)
			return null;
		if(Utils.loadIntFromBytes(this.getPages()[pgno].getData(), Position.PGNO_IN_PAGE) != 0 )
			return this.pages[pgno];

		RandomAccessFile raf = null;
		Page newPage = null;
		try{
			raf = new RandomAccessFile(this.database.getDBFile(),"r");
			raf.seek((pgno-1) * SpaceAllocation.PAGE_SIZE);

			byte[] data = new byte[SpaceAllocation.PAGE_SIZE];
			raf.read(data, 0 , SpaceAllocation.PAGE_SIZE);

			newPage = freeList.get(pgno);
			newPage.setSize(SpaceAllocation.PAGE_SIZE);
			newPage.setSectorSize(SpaceAllocation.SECTOR_SIZE);
			newPage.setData(data);
			newPage.setOffset(Utils.loadShortFromBytes(data, Position.OFFSET_IN_PAGE));
			newPage.setOverflowPgno(Utils.loadIntFromBytes(data, Position.OVERFLOWPGNO_IN_PAGE));
			newPage.setPgno(Utils.loadIntFromBytes(data, Position.PGNO_IN_PAGE));

			this.pages[pgno] = newPage;
			freeList.remove(pgno);
			return newPage;
		}catch (IOException e){
			e.printStackTrace();
		}finally {
			try{
				if(raf != null) raf.close();
			}catch (IOException e){
				e.printStackTrace();
			}
		}
		return newPage;
	}


	/**
	 *	加载数据库文件中的数据。
	 */
	public void loadDB() {
		String dbPath = this.database.getDBFile();
		if(this.database == null || this.database.getDBFile()== null || this.database.getDBFile().isEmpty())
			return;
		FileInputStream fis = null;
		try{
			fis = new FileInputStream(new File(dbPath));
			byte[] data = new byte[SpaceAllocation.PAGE_SIZE];
			int len = 0;
			int pgno = 0;

			/**
			 *	开始读取数据库：
			 * 第一次读取的根页面，后续为数据页面
			 */
			while((len = fis.read(data,0,data.length)) > 0 ){
				Page page = null;
				if(pgno < this.pageNum) {
					page = this.pages[pgno];
					page.copyData(data);
				}
				else{
					resizePages();
					page = this.pages[pgno];
					page.copyData(data);
				}
				page.setPgno(Utils.loadIntFromBytes(page.getData(), Position.PGNO_IN_PAGE));
				page.setPageType(page.getData()[Position.PGTYPE_IN_PAGE]);
				page.setOffset(Utils.loadIntFromBytes(page.getData(), Position.OFFSET_IN_PAGE));
				page.setOverflowPgno(Utils.loadIntFromBytes(page.getData(), Position.OVERFLOWPGNO_IN_PAGE));
				page.setpParent(Utils.loadIntFromBytes(page.getData(), Position.PARENT_PAGE_IN_PAGE));
				page.setpPrev(Utils.loadIntFromBytes(page.getData(), Position.PREV_PAGE_IN_PAGE));
				page.setpNext(Utils.loadIntFromBytes(page.getData(), Position.NEXT_PAGE_IN_PAGE));
				byte nCell = page.getData()[Position.CELLNUM_IN_PAGE];
				List<Integer> cells = new ArrayList<>();
				for(int i = 0 ; i<nCell; i++){
					page.addCell(Utils.loadIntFromBytes(page.getData(), Position.CELL_IN_PAGE + (i*4) ));
				}
				pgno++;
			}
		}catch (IOException e){
			e.printStackTrace();
		}finally {
			try{
				if(fis != null) fis.close();
			}catch (IOException e){
				e.printStackTrace();
			}
		}
	}
	public Page newPage(){
		Page newPage = freeList.get(1);
		((ArrayList)freeList).remove(1);
		return newPage;
	}
	public void freePage(int pgno){
	}
	private void resizePages()
	{
		int oldPageNum = this.pageNum;
		Page[] newPages = new Page[oldPageNum * 2];
		for(int i = 0; i< oldPageNum; i++){
			newPages[i] = this.pages[i];
		}
		for(int i = oldPageNum; i < newPages.length; i++){
			Page page = new Page();
			page.setData(new byte[SpaceAllocation.PAGE_SIZE]);
			page.setPgno(i);
			page.setSectorSize(SpaceAllocation.SECTOR_SIZE);
			page.setSize(SpaceAllocation.PAGE_SIZE);
			page.setOffset(SpaceAllocation.PAGE_SIZE);
			newPages[i] = page;
		}

		this.pages = newPages;
		this.pageNum = newPages.length;
	}
}
