/*
 * Copyright (C) 2018 The Sylph Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ideal.sylph.runner.flink.actuator;

import com.google.inject.Inject;
import ideal.common.ioc.Binds;
import ideal.common.jvm.JVMLauncher;
import ideal.common.jvm.JVMLaunchers;
import ideal.common.jvm.VmFuture;
import ideal.sylph.annotation.Description;
import ideal.sylph.annotation.Name;
import ideal.sylph.runner.flink.FlinkJobConfig;
import ideal.sylph.runner.flink.FlinkJobHandle;
import ideal.sylph.runner.flink.etl.FlinkNodeLoader;
import ideal.sylph.runner.flink.yarn.FlinkYarnJobLauncher;
import ideal.sylph.runtime.yarn.YarnJobContainer;
import ideal.sylph.runtime.yarn.YarnJobContainerProxy;
import ideal.sylph.spi.App;
import ideal.sylph.spi.GraphApp;
import ideal.sylph.spi.NodeLoader;
import ideal.sylph.spi.exception.SylphException;
import ideal.sylph.spi.job.EtlFlow;
import ideal.sylph.spi.job.EtlJobActuatorHandle;
import ideal.sylph.spi.job.Flow;
import ideal.sylph.spi.job.Job;
import ideal.sylph.spi.job.JobActuator;
import ideal.sylph.spi.job.JobConfig;
import ideal.sylph.spi.job.JobContainer;
import ideal.sylph.spi.job.JobHandle;
import ideal.sylph.spi.model.PipelinePluginManager;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.fusesource.jansi.Ansi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;

import java.net.URLClassLoader;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static ideal.sylph.spi.exception.StandardErrorCode.JOB_BUILD_ERROR;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.YELLOW;

@Name("StreamETL")
@Description("this is stream etl Actuator")
@JobActuator.Mode(JobActuator.ModeType.STREAM_ETL)
public class FlinkStreamEtlActuator
        extends EtlJobActuatorHandle
{
    private static final Logger logger = LoggerFactory.getLogger(FlinkStreamEtlActuator.class);
    @Inject private FlinkYarnJobLauncher jobLauncher;
    @Inject private PipelinePluginManager pluginManager;

    @NotNull
    @Override
    public Class<? extends JobConfig> getConfigParser()
    {
        return FlinkJobConfig.class;
    }

    @NotNull
    @Override
    public JobHandle formJob(String jobId, Flow inFlow, JobConfig jobConfig, URLClassLoader jobClassLoader)
            throws Exception
    {
        EtlFlow flow = (EtlFlow) inFlow;

        final JobParameter jobParameter = ((FlinkJobConfig) jobConfig).getConfig();
        JobGraph jobGraph = compile(jobId, flow, jobParameter, jobClassLoader, pluginManager);
        return new FlinkJobHandle(jobGraph);
    }

    @Override
    public JobContainer createJobContainer(@NotNull Job job, String jobInfo)
    {
        JobContainer yarnJobContainer = new YarnJobContainer(jobLauncher.getYarnClient(), jobInfo)
        {
            @Override
            public Optional<String> run()
                    throws Exception
            {
                logger.info("Instantiating SylphFlinkJob {} at yarnId {}", job.getId());
                this.setYarnAppId(null);
                ApplicationId applicationId = jobLauncher.start(job);
                this.setYarnAppId(applicationId);
                return Optional.of(applicationId.toString());
            }
        };
        return YarnJobContainerProxy.get(yarnJobContainer);
    }

    @Override
    public PipelinePluginManager getPluginManager()
    {
        return pluginManager;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("name", "streamSql")
                .add("description", ".....")
                .toString();
    }

    private static JobGraph compile(String jobId, EtlFlow flow, JobParameter jobParameter, URLClassLoader jobClassLoader, PipelinePluginManager pluginManager)
            throws Exception
    {
        //---- build flow----
        JVMLauncher<JobGraph> launcher = JVMLaunchers.<JobGraph>newJvm()
                .setCallable(() -> {
                    System.out.println("************ job start ***************");
                    StreamExecutionEnvironment execEnv = FlinkEnvFactory.getStreamEnv(jobParameter);
                    StreamTableEnvironment tableEnv = TableEnvironment.getTableEnvironment(execEnv);
                    App<StreamTableEnvironment> app = new GraphApp<StreamTableEnvironment, DataStream<Row>>()
                    {
                        @Override
                        public NodeLoader<DataStream<Row>> getNodeLoader()
                        {
                            Binds binds = Binds.builder()
                                    .bind(org.apache.flink.streaming.api.environment.StreamExecutionEnvironment.class, execEnv)
                                    .bind(org.apache.flink.table.api.StreamTableEnvironment.class, tableEnv)
                                    .bind(org.apache.flink.table.api.java.StreamTableEnvironment.class, tableEnv)
                                    //.bind(org.apache.flink.streaming.api.scala.StreamExecutionEnvironment.class, null) // execEnv
                                    //.bind(org.apache.flink.table.api.scala.StreamTableEnvironment.class, null)  // tableEnv
                                    .build();
                            return new FlinkNodeLoader(pluginManager, binds);
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
                .setConsole((line) -> System.out.println(new Ansi().fg(YELLOW).a("[" + jobId + "] ").fg(GREEN).a(line).reset()))
                .addUserURLClassLoader(jobClassLoader)
                .build();
        VmFuture<JobGraph> result = launcher.startAndGet(jobClassLoader);
        return result.get().orElseThrow(() -> new SylphException(JOB_BUILD_ERROR, result.getOnFailure()));
    }
}
