/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.protocol.internal.response.error;

import com.datastax.oss.protocol.internal.Message;
import com.datastax.oss.protocol.internal.PrimitiveCodec;
import com.datastax.oss.protocol.internal.PrimitiveSizes;
import com.datastax.oss.protocol.internal.ProtocolConstants;
import com.datastax.oss.protocol.internal.response.Error;
import com.datastax.oss.protocol.internal.util.collection.NullAllowingImmutableMap;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;

public class ReadFailure extends Error {
  /** The consistency level of the query that triggered the exception. */
  public final int consistencyLevel;
  /** The number of nodes having answered the request. */
  public final int received;
  /** The number of replicas whose response is required to achieve {@code consistencyLevel}. */
  public final int blockFor;
  /** The number of nodes that experienced a failure while executing the request. */
  public final int numFailures;
  /**
   * A map of endpoint to failure reason codes. This maps the endpoints of the replica nodes that
   * failed when executing the request to a code representing the reason for the failure. *
   */
  public final Map<InetAddress, Integer> reasonMap;
  /** Whether the replica that was asked for data responded. */
  public final boolean dataPresent;

  public ReadFailure(
      String message,
      int consistencyLevel,
      int received,
      int blockFor,
      int numFailures,
      Map<InetAddress, Integer> reasonMap,
      boolean dataPresent) {
    super(ProtocolConstants.ErrorCode.READ_FAILURE, message);
    this.consistencyLevel = consistencyLevel;
    this.received = received;
    this.blockFor = blockFor;
    this.numFailures = numFailures;
    this.reasonMap = reasonMap;
    this.dataPresent = dataPresent;
  }

  public static class SubCodec extends Error.SubCodec {
    public SubCodec(int protocolVersion) {
      super(ProtocolConstants.ErrorCode.READ_FAILURE, protocolVersion);
    }

    @Override
    public <B> void encode(B dest, Message message, PrimitiveCodec<B> encoder) {
      ReadFailure readFailure = (ReadFailure) message;
      encoder.writeString(readFailure.message, dest);
      encoder.writeUnsignedShort(readFailure.consistencyLevel, dest);
      encoder.writeInt(readFailure.received, dest);
      encoder.writeInt(readFailure.blockFor, dest);
      if (protocolVersion >= ProtocolConstants.Version.V5) {
        writeReasonMap(readFailure.reasonMap, dest, encoder);
      } else {
        encoder.writeInt(readFailure.numFailures, dest);
      }
      encoder.writeByte((byte) (readFailure.dataPresent ? 1 : 0), dest);
    }

    @Override
    public int encodedSize(Message message) {
      ReadFailure readFailure = (ReadFailure) message;
      int size =
          PrimitiveSizes.sizeOfString(readFailure.message)
              + PrimitiveSizes.SHORT // consistencyLevel
              + PrimitiveSizes.INT // received
              + PrimitiveSizes.INT // blockFor
              + PrimitiveSizes.BYTE; // dataPresent
      if (protocolVersion >= ProtocolConstants.Version.V5) {
        size += sizeOfReasonMap(readFailure.reasonMap);
      } else {
        size += PrimitiveSizes.INT; // numFailures
      }
      return size;
    }

    @Override
    public <B> Message decode(B source, PrimitiveCodec<B> decoder) {
      String message = decoder.readString(source);
      int consistencyLevel = decoder.readUnsignedShort(source);
      int received = decoder.readInt(source);
      int blockFor = decoder.readInt(source);
      int numFailures;
      Map<InetAddress, Integer> reasonMap;
      if (protocolVersion >= ProtocolConstants.Version.V5) {
        reasonMap = readReasonMap(source, decoder);
        numFailures = reasonMap.size();
      } else {
        reasonMap = Collections.emptyMap();
        numFailures = decoder.readInt(source);
      }
      boolean dataPresent = decoder.readByte(source) != 0;
      return new ReadFailure(
          message, consistencyLevel, received, blockFor, numFailures, reasonMap, dataPresent);
    }

    static <B> void writeReasonMap(Map<InetAddress, Integer> m, B dest, PrimitiveCodec<B> encoder) {
      encoder.writeInt(m.size(), dest);
      for (Map.Entry<InetAddress, Integer> entry : m.entrySet()) {
        encoder.writeInetAddr(entry.getKey(), dest);
        encoder.writeUnsignedShort(entry.getValue(), dest);
      }
    }

    static int sizeOfReasonMap(Map<InetAddress, Integer> m) {
      int size = PrimitiveSizes.INT;
      for (Map.Entry<InetAddress, Integer> entry : m.entrySet()) {
        size += PrimitiveSizes.sizeOfInetAddr(entry.getKey());
        size += PrimitiveSizes.SHORT;
      }
      return size;
    }

    static <B> Map<InetAddress, Integer> readReasonMap(B source, PrimitiveCodec<B> decoder) {
      int size = decoder.readInt(source);
      if (size == 0) {
        return Collections.emptyMap();
      } else {
        NullAllowingImmutableMap.Builder<InetAddress, Integer> builder =
            NullAllowingImmutableMap.builder(size);
        for (int i = 0; i < size; i++) {
          InetAddress key = decoder.readInetAddr(source);
          int value = decoder.readUnsignedShort(source);
          builder.put(key, value);
        }
        return builder.build();
      }
    }
  }
}
