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


    /**
     * jdk7 ConcurrentHashMap 构造方法
     *
     * @param initialCapacity  初始容量
     * @param loadFactor       负荷系数
     * @param concurrencyLevel 并发级别
     */
    public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel)
    {
        if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)
        {
            throw new IllegalArgumentException();
        }
        if (concurrencyLevel > MAX_SEGMENTS)
        {
            concurrencyLevel = MAX_SEGMENTS;
        }
        // ssize 必须是 2^n, 即 2, 4, 8, 16 ... 表示了 segments 数组的大小
        int sshift = 0;
        int ssize = 1;
        while (ssize < concurrencyLevel)
        {
            ++sshift;
            ssize <<= 1;
        }
        // segmentShift 默认是 32 - 4 = 28
        this.segmentShift = 32 - sshift;
        // segmentMask 默认是 15 即 0000 0000 0000 1111
        this.segmentMask = ssize - 1;
        if (initialCapacity > MAXIMUM_CAPACITY)
        {
            initialCapacity = MAXIMUM_CAPACITY;
        }
        int c = initialCapacity / ssize;
        if (c * ssize < initialCapacity)
        {
            ++c;
        }
        int cap = MIN_SEGMENT_TABLE_CAPACITY;
        while (cap < c)
        {
            cap <<= 1;
        }
        // 创建 segments and segments[0]
        Segment<K, V> s0 =
                new Segment<K, V>(loadFactor, (int) (cap * loadFactor),
                        (HashEntry<K, V>[]) new HashEntry[cap]);
        Segment<K, V>[] ss = (Segment<K, V>[]) new Segment[ssize];
        UNSAFE.putOrderedObject(ss, SBASE, s0); // ordered write of segments[0]
        this.segments = ss;
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
        Segment<K, V> s;
        if (value == null)
        {
            throw new NullPointerException();
        }
        int hash = hash(key);
        // 计算出 segment 下标
        int j = (hash >>> segmentShift) & segmentMask;

        // 获得 segment 对象, 判断是否为 null, 是则创建该 segment
        if ((s = (Segment<K, V>) UNSAFE.getObject
                (segments, (j << SSHIFT) + SBASE)) == null)
        {
            // 这时不能确定是否真的为 null, 因为其它线程也发现该 segment 为 null,
            // 因此在 ensureSegment 里用 cas 方式保证该 segment 安全性
            s = ensureSegment(j);
        }
        // 进入 segment 的put 流程
        return s.put(key, hash, value, false);
    }


    final V put(K key, int hash, V value, boolean onlyIfAbsent)
    {
        // 尝试加锁
        HashEntry<K, V> node = tryLock() ? null :
                // 如果不成功, 进入 scanAndLockForPut 流程
                // 如果是多核 cpu 最多 tryLock 64 次, 进入 lock 流程
                // 在尝试期间, 还可以顺便看该节点在链表中有没有, 如果没有顺便创建出来
                scanAndLockForPut(key, hash, value);

        // 执行到这里 segment 已经被成功加锁, 可以安全执行
        V oldValue;
        try
        {
            HashEntry<K, V>[] tab = table;
            int index = (tab.length - 1) & hash;
            HashEntry<K, V> first = entryAt(tab, index);
            for (HashEntry<K, V> e = first; ; )
            {
                if (e != null)
                {
                    // 更新
                    K k;
                    if ((k = e.key) == key ||
                            (e.hash == hash && key.equals(k)))
                    {
                        oldValue = e.value;
                        if (!onlyIfAbsent)
                        {
                            e.value = value;
                            ++modCount;
                        }
                        break;
                    }
                    e = e.next;
                }
                else
                {
                    // 新增
                    // 1) 之前等待锁时, node 已经被创建, next 指向链表头
                    if (node != null)
                    {
                        node.setNext(first);
                    }
                    else
                    // 2) 创建新 node
                    {
                        node = new HashEntry<K, V>(hash, key, value, first);
                    }
                    int c = count + 1;
                    // 3) 扩容
                    if (c > threshold && tab.length < MAXIMUM_CAPACITY)
                    {
                        rehash(node);
                    }
                    else
                    // 将 node 作为链表头
                    {
                        setEntryAt(tab, index, node);
                    }
                    ++modCount;
                    count = c;
                    oldValue = null;
                    break;
                }
            }
        }
        finally
        {
            unlock();
        }
        return oldValue;
    }


    /**
     * rehash方法
     *
     * @param node 节点
     */
    private void rehash(HashEntry<K, V> node)
    {
        HashEntry<K, V>[] oldTable = table;
        int oldCapacity = oldTable.length;
        int newCapacity = oldCapacity << 1;
        threshold = (int) (newCapacity * loadFactor);
        HashEntry<K, V>[] newTable =
                (HashEntry<K, V>[]) new HashEntry[newCapacity];
        int sizeMask = newCapacity - 1;
        for (int i = 0; i < oldCapacity; i++)
        {
            HashEntry<K, V> e = oldTable[i];
            if (e != null)
            {
                HashEntry<K, V> next = e.next;
                int idx = e.hash & sizeMask;
                if (next == null) // Single node on list
                {
                    newTable[idx] = e;
                }
                else
                { // Reuse consecutive sequence at same slot
                    HashEntry<K, V> lastRun = e;
                    int lastIdx = idx;
                    // 过一遍链表, 尽可能把 rehash 后 idx 不变的节点重用
                    for (HashEntry<K, V> last = next;
                         last != null;
                         last = last.next)
                    {
                        int k = last.hash & sizeMask;
                        if (k != lastIdx)
                        {
                            lastIdx = k;
                            lastRun = last;
                        }
                    }
                    newTable[lastIdx] = lastRun;
                    // 剩余节点需要新建
                    for (HashEntry<K, V> p = e; p != lastRun; p = p.next)
                    {
                        V v = p.value;
                        int h = p.hash;
                        int k = h & sizeMask;
                        HashEntry<K, V> n = newTable[k];
                        newTable[k] = new HashEntry<K, V>(h, p.key, v, n);
                    }
                }
            }
        }
        // 扩容完成, 才加入新的节点
        int nodeIndex = node.hash & sizeMask; // add the new node
        node.setNext(newTable[nodeIndex]);
        newTable[nodeIndex] = node;

        // 替换为新的 HashEntry table
        table = newTable;
    }


    /**
     * get方法
     *
     * @param key key
     * @return {@link V}
     */
    public V get(Object key)
    {
        Segment<K, V> s; // manually integrate access methods to reduce overhead
        HashEntry<K, V>[] tab;
        int h = hash(key);
        // u 为 segment 对象在数组中的偏移量
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;
        // s 即为 segment
        if ((s = (Segment<K, V>) UNSAFE.getObjectVolatile(segments, u)) != null &&
                (tab = s.table) != null)
        {
            for (HashEntry<K, V> e = (HashEntry<K, V>) UNSAFE.getObjectVolatile
                    (tab, ((long) (((tab.length - 1) & h)) << TSHIFT) + TBASE);
                 e != null; e = e.next)
            {
                K k;
                if ((k = e.key) == key || (e.hash == h && key.equals(k)))
                {
                    return e.value;
                }
            }
        }
        return null;
    }


    /**
     * 计算大小
     *
     * @return int
     */
    public int size()
    {
        // Try a few times to get accurate count. On failure due to
        // continuous async changes in table, resort to locking.
        final Segment<K, V>[] segments = this.segments;
        int size;
        boolean overflow; // true if size overflows 32 bits
        long sum; // sum of modCounts
        long last = 0L; // previous sum
        int retries = -1; // first iteration isn't retry
        try
        {
            for (; ; )
            {
                if (retries++ == RETRIES_BEFORE_LOCK)
                {
                    // 超过重试次数, 需要创建所有 segment 并加锁
                    for (int j = 0; j < segments.length; ++j)
                    {
                        ensureSegment(j).lock(); // force creation
                    }
                }
                sum = 0L;
                size = 0;
                overflow = false;
                for (int j = 0; j < segments.length; ++j)
                {
                    Segment<K, V> seg = segmentAt(segments, j);
                    if (seg != null)
                    {
                        sum += seg.modCount;
                        int c = seg.count;
                        if (c < 0 || (size += c) < 0)
                        {
                            overflow = true;
                        }
                    }
                }
                if (sum == last)
                {
                    break;
                }
                last = sum;
            }
        }
        finally
        {
            if (retries > RETRIES_BEFORE_LOCK)
            {
                for (int j = 0; j < segments.length; ++j)
                {
                    segmentAt(segments, j).unlock();
                }
            }
        }
        return overflow ? Integer.MAX_VALUE : size;
    }


}
