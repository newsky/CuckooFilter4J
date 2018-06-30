package io.sky.hash.cukoo;

/**
 * Created by sky
 * on 2018/6/30.
 */
interface HashFactory<T> {
  //根据which来选择散列函数，并返回hash值
  int hash(T x, int which);

  //返回集合中散列函数的个数
  int getNumberOfFunctions();
  //获取到新的散列函数
  void generateNewFunctions();

}
