package mao.t6;

/**
 * Project name(项目名称)：java并发编程_ConcurrentHashMap
 * Package(包名): mao.t6
 * Class(类名): JDK8HashMap
 * Author(作者）: mao
 * Author QQ：1296193245
 * GitHub：https://github.com/maomao124/
 * Date(创建日期)： 2022/9/14
 * Time(创建时间)： 21:16
 * Version(版本): 1.0
 * Description(描述)： 无
 */

public class JDK8HashMap<K, V>
{
    // 默认为 0
    // 当初始化时, 为 -1
    // 当扩容时, 为 -(1 + 扩容线程数)
    // 当初始化或扩容完成后，为 下一次的扩容的阈值大小
    private transient volatile int sizeCtl;

    // 整个 ConcurrentHashMap 就是一个 Node[]
    static class Node<K, V> implements Map.Entry<K, V>
    {
    }

    // hash 表
    transient volatile Node<K, V>[] table;

    // 扩容时的 新 hash 表
    private transient volatile Node<K, V>[] nextTable;

    // 扩容时如果某个 bin 迁移完毕, 用 ForwardingNode 作为旧 table bin 的头结点
    static final class ForwardingNode<K, V> extends Node<K, V>
    {
    }

    // 用在 compute 以及 computeIfAbsent 时, 用来占位, 计算完成后替换为普通 Node
    static final class ReservationNode<K, V> extends Node<K, V>
    {
    }

    // 作为 treebin 的头节点, 存储 root 和 first
    static final class TreeBin<K, V> extends Node<K, V>
    {
    }

    // 作为 treebin 的节点, 存储 parent, left, right
    static final class TreeNode<K, V> extends Node<K, V>
    {
    }


    /**
     * ConcurrentHashMap构造方法
     *
     * @param initialCapacity  初始容量
     * @param loadFactor       负载因子
     * @param concurrencyLevel 并发级别
     */
    public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel)
    {
        if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0)
        {
            throw new IllegalArgumentException();
        }
        if (initialCapacity < concurrencyLevel) // Use at least as many bins
        {
            initialCapacity = concurrencyLevel; // as estimated threads
        }
        long size = (long) (1.0 + (long) initialCapacity / loadFactor);
        // tableSizeFor 仍然是保证计算的大小是 2^n, 即 16,32,64 ...
        int cap = (size >= (long) MAXIMUM_CAPACITY) ?
                MAXIMUM_CAPACITY : tableSizeFor((int) size);
        this.sizeCtl = cap;
    }


    /**
     * ConcurrentHashMap 根据key获取value
     *
     * @param key key
     * @return {@link V}
     */
    public V get(Object key)
    {
        Node<K, V>[] tab;
        Node<K, V> e, p;
        int n, eh;
        K ek;
        // spread 方法能确保返回结果是正数
        int h = spread(key.hashCode());
        if ((tab = table) != null
                && (n = tab.length) > 0
                && (e = tabAt(tab, (n - 1) & h)) != null)
        {
            // 如果头结点已经是要查找的 key
            if ((eh = e.hash) == h)
            {
                if ((ek = e.key) == key || (ek != null && key.equals(ek)))
                {
                    return e.val;
                }
            }
            // hash 为负数表示该 bin 在扩容中或是 treebin, 这时调用 find 方法来查找
            else if (eh < 0)
            {
                return (p = e.find(h, key)) != null ? p.val : null;
            }
            // 正常遍历链表, 用 equals 比较
            while ((e = e.next) != null)
            {
                if (e.hash == h &&
                        ((ek = e.key) == key || (ek != null && key.equals(ek))))
                {
                    return e.val;
                }
            }
        }
        return null;
    }


    /**
     * put方法
     *
     * @param key   key
     * @param value value
     * @return {@link V}
     */
    public V put(K key, V value)
    {
        return putVal(key, value, false);
    }


    /**
     * putVal方法
     *
     * @param key          key
     * @param value        value
     * @param onlyIfAbsent fasle即存在就覆盖
     * @return {@link V}
     */
    final V putVal(K key, V value, boolean onlyIfAbsent)
    {
        if (key == null || value == null)
        {
            throw new NullPointerException();
        }
        // 其中 spread 方法会综合高位低位, 具有更好的 hash 性
        int hash = spread(key.hashCode());
        int binCount = 0;
        for (Node<K, V>[] tab = table; ; )
        {
            // f 是链表头节点
            // fh 是链表头结点的 hash
            // i 是链表在 table 中的下标
            // n 为table的长度 tab.length
            Node<K, V> f;
            int n, i, fh;
            // 要创建 table，因为是懒惰初始化
            if (tab == null || (n = tab.length) == 0)
            // 初始化 table 使用了 cas, 无需 synchronized 创建成功, 进入下一轮循环
            {
                tab = initTable();
            }
            // 要创建链表头节点
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null)
            {
                // 添加链表头使用了 cas, 无需 synchronized
                if (casTabAt(tab, i, null,
                        new Node<K, V>(hash, key, value, null)))
                {
                    break;
                }
            }
            // 帮忙扩容
            else if ((fh = f.hash) == MOVED)
            // 帮忙之后, 进入下一轮循环
            {
                tab = helpTransfer(tab, f);
            }
            else
            {
                V oldVal = null;
                // 锁住链表头节点
                synchronized (f)
                {
                    // 再次确认链表头节点没有被移动
                    if (tabAt(tab, i) == f)
                    {
                        // 链表
                        if (fh >= 0)
                        {
                            binCount = 1;
                            // 遍历链表
                            for (Node<K, V> e = f; ; ++binCount)
                            {
                                K ek;
                                // 找到相同的 key
                                if (e.hash == hash &&
                                        ((ek = e.key) == key ||
                                                (ek != null && key.equals(ek))))
                                {
                                    oldVal = e.val;
                                    // 更新
                                    if (!onlyIfAbsent)
                                    {
                                        e.val = value;
                                    }
                                    break;
                                }
                                Node<K, V> pred = e;
                                // 已经是最后的节点了, 新增 Node, 追加至链表尾
                                if ((e = e.next) == null)
                                {
                                    pred.next = new Node<K, V>(hash, key, value, null);
                                    break;
                                }
                            }
                        }
                        // 红黑树
                        else if (f instanceof TreeBin)
                        {
                            Node<K, V> p;
                            binCount = 2;
                            // putTreeVal 会看 key 是否已经在树中, 是, 则返回对应的 TreeNode
                            if ((p = ((TreeBin<K, V>) f).putTreeVal(hash, key,
                                    value)) != null)
                            {
                                oldVal = p.val;
                                if (!onlyIfAbsent)
                                {
                                    p.val = value;
                                }
                            }
                        }
                    }
                    // 释放链表头节点的锁
                }

                if (binCount != 0)
                {
                    if (binCount >= TREEIFY_THRESHOLD)
                    // 如果链表长度 >= 树化阈值(8), 进行链表转为红黑树
                    {
                        treeifyBin(tab, i);
                    }
                    if (oldVal != null)
                    {
                        return oldVal;
                    }
                    break;
                }
            }
        }
        // 增加 size 计数
        addCount(1L, binCount);
        return null;
    }

    /**
     * 初始化表
     *
     * @return {@link Node}<{@link K}, {@link V}>{@link []}
     */
    private final Node<K, V>[] initTable()
    {
        Node<K, V>[] tab;
        int sc;
        while ((tab = table) == null || tab.length == 0)
        {
            if ((sc = sizeCtl) < 0)
            {
                Thread.yield();
            }
            // 尝试将 sizeCtl 设置为 -1（表示初始化 table）
            else if (U.compareAndSwapInt(this, SIZECTL, sc, -1))
            {
                // 获得锁, 创建 table, 这时其它线程会在 while() 循环中 yield 直至 table 创建
                try
                {
                    if ((tab = table) == null || tab.length == 0)
                    {
                        int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                        Node<K, V>[] nt = (Node<K, V>[]) new Node<?, ?>[n];
                        table = tab = nt;
                        sc = n - (n >>> 2);
                    }
                }
                finally
                {
                    sizeCtl = sc;
                }
                break;
            }
        }
        return tab;
    }

    /**
     * 添加计数
     * check 是之前 binCount 的个数
     *
     * @param x     x
     * @param check 检查
     */
    private final void addCount(long x, int check)
    {
        CounterCell[] as;
        long b, s;
        if (
            // 已经有了 counterCells, 向 cell 累加
                (as = counterCells) != null ||
                        // 还没有, 向 baseCount 累加
                        !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)
        )
        {
            CounterCell a;
            long v;
            int m;
            boolean uncontended = true;
            if (
                // 还没有 counterCells
                    as == null || (m = as.length - 1) < 0 ||
                            // 还没有 cell
                            (a = as[ThreadLocalRandom.getProbe() & m]) == null ||
                            // cell cas 增加计数失败
                            !(uncontended = U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))
            )
            {
                // 创建累加单元数组和cell, 累加重试
                fullAddCount(x, uncontended);
                return;
            }
            if (check <= 1)
            {
                return;
            }
            // 获取元素个数
            s = sumCount();
        }
        if (check >= 0)
        {
            Node<K, V>[] tab, nt;
            int n, sc;
            while (s >= (long) (sc = sizeCtl) && (tab = table) != null &&
                    (n = tab.length) < MAXIMUM_CAPACITY)
            {
                int rs = resizeStamp(n);
                if (sc < 0)
                {
                    if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                            sc == rs + MAX_RESIZERS || (nt = nextTable) == null ||
                            transferIndex <= 0)
                    {
                        break;
                    }
                    // newtable 已经创建了，帮忙扩容
                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
                    {
                        transfer(tab, nt);
                    }
                }
                // 需要扩容，这时 newtable 未创建
                else if (U.compareAndSwapInt(this, SIZECTL, sc,
                        (rs << RESIZE_STAMP_SHIFT) + 2))
                {
                    transfer(tab, null);
                }
                s = sumCount();
            }
        }
    }


    /**
     * 计算大小
     *
     * @return int
     */
    public int size()
    {
        long n = sumCount();
        return ((n < 0L) ? 0 :
                (n > (long) Integer.MAX_VALUE) ? Integer.MAX_VALUE :
                        (int) n);
    }

    /**
     * 和计数
     *
     * @return long
     */
    final long sumCount()
    {
        CounterCell[] as = counterCells;
        CounterCell a;
        // 将 baseCount 计数与所有 cell 计数累加
        long sum = baseCount;
        if (as != null)
        {
            for (int i = 0; i < as.length; ++i)
            {
                if ((a = as[i]) != null)
                {
                    sum += a.value;
                }
            }
        }
        return sum;
    }


}
