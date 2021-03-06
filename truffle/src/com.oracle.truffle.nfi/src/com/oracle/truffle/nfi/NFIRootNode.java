/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.types.NativeSource;

class NFIRootNode extends RootNode {

    static class LookupAndBindNode extends Node {

        private final String name;
        private final String signature;

        @Child Node read;
        @Child Node bind;

        LookupAndBindNode(String name, String signature) {
            this.name = name;
            this.signature = signature;
            this.read = Message.READ.createNode();
            this.bind = Message.INVOKE.createNode();
        }

        TruffleObject execute(TruffleObject library) {
            try {
                TruffleObject symbol = (TruffleObject) ForeignAccess.sendRead(read, library, name);
                return (TruffleObject) ForeignAccess.sendInvoke(bind, symbol, "bind", signature);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }
    }

    @Child DirectCallNode loadLibrary;
    @Children LookupAndBindNode[] lookupAndBind;

    NFIRootNode(NFILanguage language, DirectCallNode loadLibrary, NativeSource source) {
        super(language);
        this.loadLibrary = loadLibrary;
        this.lookupAndBind = new LookupAndBindNode[source.preBoundSymbolsLength()];

        for (int i = 0; i < lookupAndBind.length; i++) {
            lookupAndBind[i] = new LookupAndBindNode(source.getPreBoundSymbol(i), source.getPreBoundSignature(i));
        }
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        TruffleObject library = (TruffleObject) loadLibrary.call(new Object[0]);
        if (lookupAndBind.length == 0) {
            return library;
        } else {
            NFILibrary ret = new NFILibrary(library);
            for (int i = 0; i < lookupAndBind.length; i++) {
                ret.preBindSymbol(lookupAndBind[i].name, lookupAndBind[i].execute(library));
            }
            return ret;
        }
    }
}
