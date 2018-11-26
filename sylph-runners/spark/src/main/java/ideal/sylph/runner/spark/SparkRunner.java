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
package ideal.sylph.runner.spark;

import com.google.inject.Injector;
import com.google.inject.Scopes;
import ideal.common.bootstrap.Bootstrap;
import ideal.common.classloader.DirClassLoader;
import ideal.sylph.runtime.yarn.YarnModule;
import ideal.sylph.spi.Runner;
import ideal.sylph.spi.RunnerContext;
import ideal.sylph.spi.job.ContainerFactory;
import ideal.sylph.spi.job.JobActuatorHandle;
import ideal.sylph.spi.model.PipelinePluginInfo;
import ideal.sylph.spi.model.PipelinePluginManager;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static ideal.sylph.spi.model.PipelinePluginManager.filterRunnerPlugins;
import static java.util.Objects.requireNonNull;

public class SparkRunner
        implements Runner
{
    @Override
    public Set<JobActuatorHandle> create(RunnerContext context)
    {
        requireNonNull(context, "context is null");
        String sparkHome = requireNonNull(System.getenv("SPARK_HOME"), "SPARK_HOME not setting");
        checkArgument(new File(sparkHome).exists(), "SPARK_HOME " + sparkHome + " not exists");

        ClassLoader classLoader = this.getClass().getClassLoader();
        try {
            if (classLoader instanceof DirClassLoader) {
                ((DirClassLoader) classLoader).addDir(new File(sparkHome, "jars"));
            }

            Bootstrap app = new Bootstrap(
                    new SparkRunnerModule(),
                    new YarnModule(),
                    binder -> {
                        //------------------------
                        binder.bind(PipelinePluginManager.class)
                                .toProvider(() -> createPipelinePluginManager(context))
                                .in(Scopes.SINGLETON);
                    });
            Injector injector = app.strictConfig()
                    .name(this.getClass().getSimpleName())
                    .setRequiredConfigurationProperties(Collections.emptyMap())
                    .initialize();

            return Stream.of(StreamEtlActuator.class, Stream2EtlActuator.class, SparkSubmitActuator.class)
                    .map(injector::getInstance).collect(Collectors.toSet());
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Class<? extends ContainerFactory> getContainerFactory()
    {
        return SparkContainerFactory.class;
    }

    private static PipelinePluginManager createPipelinePluginManager(RunnerContext context)
    {
        final Set<String> keyword = Stream.of(
                org.apache.spark.streaming.StreamingContext.class,
                org.apache.spark.sql.SparkSession.class,
                org.apache.spark.streaming.dstream.DStream.class,
                org.apache.spark.sql.Dataset.class
        ).map(Class::getName).collect(Collectors.toSet());

        final Set<PipelinePluginInfo> runnerPlugins =
                filterRunnerPlugins(context.getFindPlugins(), keyword, SparkRunner.class);

        return new PipelinePluginManager()
        {
            @Override
            public Set<PipelinePluginInfo> getAllPlugins()
            {
                return runnerPlugins;
            }
        };
    }
}
