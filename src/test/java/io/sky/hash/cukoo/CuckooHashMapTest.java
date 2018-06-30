package io.sky.hash.cukoo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by sky
 * on 2018/6/30.
 */
public class CuckooHashMapTest {
  HashFactory<String> hashFactory;

  @Before
  public void setUp() throws Exception {
    hashFactory=new StringHashFactory();
  }

  @After
  public void tearDown() throws Exception {

  }

  @Test
  public void insert() throws Exception {
//定义布谷鸟散列
    CuckooHashMap<String> cuckooHashTable = new CuckooHashMap<String>(hashFactory, 5);
    String[] strs = {"abc","aba","abcc","abca"};
    //插入
    for (int i = 0; i < strs.length; i ++){
      cuckooHashTable.insert(strs[i]);
    }
    //打印表
    cuckooHashTable.printArray();

  }

}