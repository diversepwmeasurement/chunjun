/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.flinkx.socket.util;

import com.dtstack.flinkx.decoder.DecodeEnum;
import com.dtstack.flinkx.decoder.IDecode;
import com.dtstack.flinkx.decoder.JsonDecoder;
import com.dtstack.flinkx.decoder.TextDecoder;
import com.dtstack.flinkx.util.ExceptionUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.commons.lang.StringUtils;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;

import static com.dtstack.flinkx.socket.constants.SocketCons.KEY_EXIT0;

/**
 * 自定义handler
 * @author kunni@dtstack.com
 */

public class DtClientHandler extends ChannelInboundHandlerAdapter {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    protected SynchronousQueue<Row> queue;

    protected IDecode decoder;

    protected String encoding;

    public DtClientHandler(SynchronousQueue<Row> queue, String decoder, String encoding){
        this.queue = queue;
        this.decoder = getDecoder(decoder);
        this.encoding = encoding;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        Map<String, Object> event = decoder.decode(byteBuf.toString(Charset.forName(encoding)));
        Row row = new Row(event.size());
        int count = 0;
        for(Map.Entry<String, Object> entry : event.entrySet()){
            row.setField(count++, entry.getValue());
        }
        try{
            queue.put(row);
        }catch (InterruptedException e){
            LOG.error(ExceptionUtil.getErrorMessage(e));
        }
    }

    public IDecode getDecoder(String codeC){
        switch (DecodeEnum.valueOf(StringUtils.upperCase(codeC))){
            case JSON:
                return new JsonDecoder();
            case TEXT:
            default:
                return new TextDecoder();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String error = ExceptionUtil.getErrorMessage(cause);
        LOG.error(error);
        ctx.close();
        try {
            queue.put(Row.of(KEY_EXIT0 + error));
        } catch (InterruptedException ex) {
            LOG.error(ExceptionUtil.getErrorMessage(ex));
        }
    }
}