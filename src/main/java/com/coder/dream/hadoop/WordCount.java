package com.coder.dream.hadoop;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.*;

import java.io.IOException;
import java.util.Iterator;
import java.util.StringTokenizer;

/**
 * Created by Administrator on 2015/10/15.
 */
public class WordCount {

    public static class WordCountMapper extends MapReduceBase implements Mapper<Object,Text,Text,IntWritable>{

        private final static IntWritable one = new IntWritable(1);
        private Text word = new Text();

        @Override
        public void map(Object key, Text value, OutputCollector<Text, IntWritable> outputCollector, Reporter reporter) throws IOException {
            StringTokenizer tokenizer = new StringTokenizer(value.toString());
            while(tokenizer.hasMoreTokens()){
                word.set(tokenizer.nextToken());
                outputCollector.collect(word,one);
            }
        }
    }

    public static class WordCountReducer extends MapReduceBase implements Reducer<Text,IntWritable,Text,IntWritable>{

        private IntWritable result = new IntWritable();

        @Override
        public void reduce(Text text, Iterator<IntWritable> iterator, OutputCollector<Text, IntWritable> outputCollector, Reporter reporter) throws IOException {
            int sum = 0;
            while(iterator.hasNext()){
                sum += iterator.next().get();
            }
            result.set(sum);
        }
    }

    public static void main(String[] args) throws Exception{
        String input = "hdfs://master:9000/user/hadoop/input";
        String output = "hdfs://master:9000/user/hadoop/output3";

        JobConf conf = new JobConf(WordCount.class);
        conf.setJobName("WordCount");
        conf.addResource("classpath:/hadoop/core-site.xml");
        conf.addResource("classpath:/hadoop/hdfs-site.xml");
        conf.addResource("classpath:/hadoop/mapred-site.xml");

        conf.setOutputKeyClass(Text.class);
        conf.setOutputValueClass(IntWritable.class);

        conf.setMapperClass(WordCountMapper.class);
        conf.setCombinerClass(WordCountReducer.class);
        conf.setReducerClass(WordCountReducer.class);

        conf.setInputFormat(TextInputFormat.class);
        conf.setOutputFormat(TextOutputFormat.class);

        FileInputFormat.setInputPaths(conf,new Path(input));
        FileOutputFormat.setOutputPath(conf,new Path(output));
        JobClient.runJob(conf);
        System.exit(0);
    }
}
