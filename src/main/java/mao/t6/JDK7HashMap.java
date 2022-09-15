package mao.t6;

/**
 * Project name(项目名称)：java并发编程_ConcurrentHashMap
 * Package(包名): mao.t6
 * Class(类名): JDK7HashMap
 * Author(作者）: mao
 * Author QQ：1296193245
 * GitHub：https://github.com/maomao124/
 * Date(创建日期)： 2022/9/14
 * Time(创建时间)： 21:05
 * Version(版本): 1.0
 * Description(描述)： 无
 */

public class JDK7HashMap
{
    void transfer(Entry[] newTable, boolean rehash)
    {
        int newCapacity = newTable.length;
        for (Entry<K, V> e : table)
        {
            while (null != e)
            {
                Entry<K, V> next = e.next;
                // 1 处
                if (rehash)
                {
                    e.hash = null == e.key ? 0 : hash(e.key);
                }
                int i = indexFor(e.hash, newCapacity);
                // 2 处
                // 将新元素加入 newTable[i], 原 newTable[i] 作为新元素的 next
                e.next = newTable[i];
                newTable[i] = e;
                e = next;
            }
        }
    }
















}
