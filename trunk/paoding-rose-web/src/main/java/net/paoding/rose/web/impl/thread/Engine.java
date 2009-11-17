/*
 * $ID$
 */
/*
 * Copyright 2007-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.paoding.rose.web.impl.thread;

import net.paoding.rose.web.impl.mapping.Mapping;
import net.paoding.rose.web.impl.thread.tree.Rose;

/**
 * 一个 {@link Engine} 封装了对某种符合要求的请求的某种处理。Rose 对一次WEB请求的处理最终落实为对一些列的
 * {@link Engine}的有序调用，每个 {@link Engine} 负责处理其中需要处理的逻辑，共同协作完成 Rose 的职责。
 * <p>
 * 在一个Rose应用中，存在着“很多的、不同的” {@link Engine}实例，这些实例根据映射关系组成在一个树状的结构中。
 * 
 * @see Rose
 * @see Mapping
 * @author 王志亮 [qieqie.wang@gmail.com]
 */
public interface Engine {

    /**
     * 处理web请求
     * 
     * @param inv
     * @param mr
     * @param instruction
     * @param chain
     * @throws Throwable
     */
    public Object invoke(Rose rose, MatchResult<? extends Engine> mr, Object instruction,
            EngineChain chain) throws Throwable;

    /**
     * 销毁该引擎，在系统关闭或其他情况时
     * 
     * @throws Throwable
     */
    public void destroy();
}
