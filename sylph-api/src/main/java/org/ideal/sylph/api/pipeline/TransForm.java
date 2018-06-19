package org.ideal.sylph.api.pipeline;

import org.ideal.sylph.api.PipelinePlugin;

/**
 * Created by ideal on 17-5-8. 转换
 */
public interface TransForm<T>
        extends PipelinePlugin
{
    T transform(final T stream);
}
