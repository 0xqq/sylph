package ideal.sylph.runner.flink.utils;

import com.google.common.collect.ImmutableSet;
import ideal.sylph.common.jvm.JVMLauncher;
import ideal.sylph.common.jvm.JVMLaunchers;
import ideal.sylph.common.jvm.VmFuture;
import ideal.sylph.runner.flink.FlinkJobHandle;
import ideal.sylph.runner.flink.FlinkRunner;
import ideal.sylph.runner.flink.JobParameter;
import ideal.sylph.runner.flink.etl.FlinkPluginLoaderImpl;
import ideal.sylph.spi.App;
import ideal.sylph.spi.GraphApp;
import ideal.sylph.spi.NodeLoader;
import ideal.sylph.spi.exception.SylphException;
import ideal.sylph.spi.job.Flow;
import ideal.sylph.spi.job.JobHandle;
import org.apache.flink.calcite.shaded.com.google.common.collect.ImmutableList;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.StreamTableEnvironment;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.types.Row;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static ideal.sylph.spi.exception.StandardErrorCode.JOB_BUILD_ERROR;

public final class FlinkJobUtil
{
    private FlinkJobUtil() {}

    private static final Logger logger = LoggerFactory.getLogger(FlinkJobUtil.class);

    public static JobHandle createJob(String jobId, Flow flow, URLClassLoader jobClassLoader)
            throws Exception
    {
        List<URL> userJars = getAppClassLoaderJars(jobClassLoader);
        //---------编译job-------------
        JobGraph jobGraph = compile(jobId, flow, 2, jobClassLoader);
        //----------------设置状态----------------
        JobParameter state = new JobParameter()
                .queue("default")
                .taskManagerCount(2) //-yn 注意是executer个数
                .taskManagerMemoryMb(1024) //1024mb
                .taskManagerSlots(1) // -ys
                .jobManagerMemoryMb(1024) //-yjm
                .appTags(ImmutableSet.of("demo1", "demo2"))
                .setUserProvidedJar(getUserAdditionalJars(userJars))
                .setYarnJobName(jobId);

        return FlinkJobHandle.newJob()
                .setJobParameter(state)
                .setJobGraph(jobGraph)
                .build();
    }

    /**
     * 对job 进行编译
     */
    private static JobGraph compile(String jobId, Flow flow, int parallelism, URLClassLoader jobClassLoader)
            throws Exception
    {
        JVMLauncher<JobGraph> launcher = JVMLaunchers.<JobGraph>newJvm()
                .setCallable(() -> {
                    System.out.println("************ job start ***************");
                    StreamExecutionEnvironment execEnv = StreamExecutionEnvironment.createLocalEnvironment();
                    execEnv.setParallelism(parallelism);
                    StreamTableEnvironment tableEnv = TableEnvironment.getTableEnvironment(execEnv);
                    App<StreamTableEnvironment> app = new GraphApp<StreamTableEnvironment, DataStream<Row>>()
                    {
                        @Override
                        public NodeLoader<StreamTableEnvironment, DataStream<Row>> getNodeLoader()
                        {
                            return new FlinkPluginLoaderImpl();
                        }

                        @Override
                        public StreamTableEnvironment getContext()
                        {
                            return tableEnv;
                        }

                        @Override
                        public void build()
                                throws Exception
                        {
                            this.buildGraph(jobId, flow).run();
                        }
                    };
                    app.build();
                    return execEnv.getStreamGraph().getJobGraph();
                })
                .addUserURLClassLoader(jobClassLoader)
                .build();
        VmFuture<JobGraph> result = launcher.startAndGet(jobClassLoader);
        return result.get().orElseThrow(() -> new SylphException(JOB_BUILD_ERROR, result.getOnFailure()));
    }

    private static List<URL> getAppClassLoaderJars(final ClassLoader jobClassLoader)
    {
        ImmutableList.Builder<URL> builder = ImmutableList.builder();
        if (jobClassLoader instanceof URLClassLoader) {
            builder.add(((URLClassLoader) jobClassLoader).getURLs());

            final ClassLoader parentClassLoader = jobClassLoader.getParent();
            if (parentClassLoader instanceof URLClassLoader) {
                builder.add(((URLClassLoader) parentClassLoader).getURLs());
            }
        }
        return builder.build();
    }

    /**
     * 获取任务额外需要的jar
     */
    private static Iterable<Path> getUserAdditionalJars(List<URL> userJars)
    {
        return userJars.stream().map(jar -> {
            try {
                final URI uri = jar.toURI();
                final File file = new File(uri);
                if (file.exists() && file.isFile()) {
                    return new Path(uri);
                }
            }
            catch (Exception e) {
                logger.warn("add user jar error with URISyntaxException {}", jar);
            }
            return null;
        }).filter(x -> Objects.nonNull(x) && !x.getName().startsWith(FlinkRunner.FLINK_DIST)).collect(Collectors.toList());
    }
}
