/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import java.util.zip.ZipInputStream;

class StreamZipInput extends ZipInput {

    private final ZipInputStream in;

    public StreamZipInput(InputStream in) {
        this.in = new ZipInputStream(in);
    }

    @Nullable
    @Override
    public ZipEntry getNextEntry() throws IOException {
        java.util.zip.ZipEntry nextEntry = in.getNextEntry();
        return nextEntry == null ? null : new JdkZipEntry(nextEntry, new Supplier<InputStream>() {
            @Override
            public InputStream get() {
                return in;
            }
        });
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
