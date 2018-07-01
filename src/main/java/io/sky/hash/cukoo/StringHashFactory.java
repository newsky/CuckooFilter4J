package io.sky.hash.cukoo;

import io.sky.util.HashFactory;

/**
 * Created by sky
 * on 2018/6/30.
 */
public class StringHashFactory implements HashFactory<String> {
  //根据which选取不同的散列函数
  @Override
  public int hash(String x, int which) {
    int hashVal = 0;
    switch (which){
      case 0:{
        for (int i = 0; i < x.length(); i ++){
          hashVal += x.charAt(i);
        }
        break;
      }
      case 1:
        for (int i = 0; i < x.length(); i ++){
          hashVal = 37 * hashVal + x.charAt(i);
        }
        break;
    }
    return hashVal;
  }

  //返回散列函数集合的个数
  @Override
  public int getNumberOfFunctions() {
    return 2;
  }

  @Override
  public void generateNewFunctions() {

  }
}
