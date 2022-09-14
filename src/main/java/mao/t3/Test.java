package mao.t3;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Project name(项目名称)：java并发编程_ConcurrentHashMap
 * Package(包名): mao.t3
 * Class(类名): Test
 * Author(作者）: mao
 * Author QQ：1296193245
 * GitHub：https://github.com/maomao124/
 * Date(创建日期)： 2022/9/14
 * Time(创建时间)： 14:17
 * Version(版本): 1.0
 * Description(描述)： 无
 */

public class Test
{
    private static final String word = "abcedfghijklmnopqrstuvwxyz";

    /**
     * 写
     */
    @SuppressWarnings("all")
    private static void write()
    {
        int length = word.length();
        int count = 200;
        List<String> list = new ArrayList<>(length * count);
        //加入到集合中
        for (int i = 0; i < length; i++)
        {
            char ch = word.charAt(i);
            for (int j = 0; j < count; j++)
            {
                list.add(String.valueOf(ch));
            }
        }
        //打乱
        Collections.shuffle(list);

        //写入到文件
        for (int i = 0; i < 26; i++)
        {
            new File("./file/").mkdir();

            File file = new File(".\\file\\" + (i + 1) + ".txt");
            if (!file.exists())
            {
                try
                {
                    file.createNewFile();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            try (PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(file))))
            {
                String collect = String.join("\n", list.subList(i * count, (i + 1) * count));
                out.print(collect);
            }
            catch (IOException ignored)
            {
                //ignored.printStackTrace();
            }
        }
    }


    /**
     * 读
     *
     * @param supplier 供应商
     * @param consumer 消费者
     */
    private static <V> void read(Supplier<Map<String, V>> supplier,
                                 BiConsumer<Map<String, V>, List<String>> consumer)
    {
        Map<String, V> counterMap = supplier.get();
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i <= 26; i++)
        {
            int idx = i;
            Thread thread = new Thread(() ->
            {
                List<String> words = readFromFile(idx);
                //计数
                consumer.accept(counterMap, words);
            });
            threads.add(thread);
        }
        threads.forEach(Thread::start);
        threads.forEach(t ->
        {
            try
            {
                t.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        });

        //打印
        Set<String> words = counterMap.keySet();
        int i = 0;
        for (String word : words)
        {
            System.out.print(word + "=" + counterMap.get(word) + "\t\t");
            i++;
            if (i % 3 == 0)
            {
                System.out.println();
            }
        }
    }

    /**
     * 从文件读取
     *
     * @param i 文件序号
     * @return {@link List}<{@link String}>
     */
    public static List<String> readFromFile(int i)
    {
        ArrayList<String> words = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream("./file/"
                + i + ".txt"))))
        {
            while (true)
            {
                String word = bufferedReader.readLine();
                if (word == null)
                {
                    break;
                }
                words.add(word);
            }
            return words;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args)
    {
        write();

        long start = System.currentTimeMillis();

        read(new Supplier<Map<String, LongAdder>>()
        {
            @Override
            public Map<String, LongAdder> get()
            {
                return new ConcurrentHashMap<>();
            }
        }, new BiConsumer<Map<String, LongAdder>, List<String>>()
        {
            @Override
            public void accept(Map<String, LongAdder> stringIntegerMap, List<String> strings)
            {
                for (String word : strings)
                {
                    stringIntegerMap.computeIfAbsent(word, (key) -> new LongAdder()).increment();
                }
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                System.out.println("\n");
                long end = System.currentTimeMillis();
                System.out.println("运行时间：" + (end - start) + "ms");
            }
        }));
    }
}
