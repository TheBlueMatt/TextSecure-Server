/**
 * Copyright (C) 2014 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.storage;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter;
import org.atmosphere.cpr.AtmosphereResponse;
import org.whispersystems.textsecuregcm.entities.EncryptedOutgoingMessage;
import org.whispersystems.textsecuregcm.util.Pair;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class StoredMessageManager {
  private class Writer {
    private long messageId = 0;
    private AtmosphereResponse resp;
    private Map<Long, SettableFuture<Boolean>> futuresMap = Collections.synchronizedMap(new HashMap<Long, SettableFuture<Boolean>>());

    Writer(AtmosphereResponse resp) { this.resp = resp; }

    synchronized void sendMessage(final Device destination, SettableFuture<Boolean> future, String message) throws IOException {
      final long id = messageId++;
      PrintWriter stream = resp.getWriter();

      stream.write("{\"id\": " + id + ", \"message\": \"" + message + "\"}");

      Futures.addCallback(future, new FutureCallback<Boolean>() {
        @Override
        public void onSuccess(Boolean result) {
          if (!result)
            disconnect(destination);
          else
            futuresMap.remove(id);
        }

        @Override public void onFailure(Throwable t) { throw new RuntimeException(t); }
      });
      futuresMap.put(id, future);
    }

    synchronized void receiveAck(long id) {
      SettableFuture<Boolean> future = futuresMap.get(id);
      if (future != null)
        future.set(true);
    }
  }

  private void disconnect(Device device) {
    Writer writer = messageWriters.remove(new Pair<>(device.getNumber(), device.getDeviceId()));
    if (writer != null) {
      try { writer.resp.close(); } catch (IOException e) { /* Already closed */ }
      for (SettableFuture<Boolean> future : writer.futuresMap.values())
        future.set(false);
    }
  }

  StoredMessages storedMessages;
  private final Map<Pair<String, Long>, Writer> messageWriters = Collections.synchronizedMap(new HashMap<Pair<String, Long>, Writer>());
  private ScheduledExecutorService futureTimeoutExecutor = new ScheduledThreadPoolExecutor(2);

  public StoredMessageManager(StoredMessages storedMessages) {
    this.storedMessages = storedMessages;
  }

  public void sendMessage(final Device device, EncryptedOutgoingMessage outgoingMessage) throws IOException {
    final String message = outgoingMessage.serialize();
    SettableFuture<Boolean> future = writeMessage(device, message);
    Futures.addCallback(future, new FutureCallback<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        if (!result)
          storedMessages.insert(device.getId(), message);
      }

      @Override public void onFailure(Throwable t) { throw new RuntimeException(t); }
    });
  }

  public void registerListener(final Device device, AtmosphereResource resource) {
    AtmosphereResponse response = resource.getResponse();
    messageWriters.put(new Pair<>(device.getNumber(), device.getDeviceId()), new Writer(response));
    resource.addEventListener(new AtmosphereResourceEventListenerAdapter() {
      @Override
      public void onDisconnect(AtmosphereResourceEvent event) {
        if (!event.isClosedByApplication())
          disconnect(device);
      }
    });
    for (final Pair<Long, String> message : storedMessages.getMessagesForAccountId(device.getId())) {
      ListenableFuture<Boolean> future = writeMessage(device, message.second());
      Futures.addCallback(future, new FutureCallback<Boolean>() {
        @Override
        public void onSuccess(Boolean result) {
          if (result)
            storedMessages.removeStoredMessage(message.first());
        }

        @Override public void onFailure(Throwable t) { throw new RuntimeException(t); }
      });
    }
  }

  public void receivedAck(Device device, long id) {
    Writer writer = messageWriters.get(new Pair<>(device.getNumber(), device.getDeviceId()));
    if (writer != null)
      writer.receiveAck(id);
  }

  /**
   * Tries to deliver the given message, returning a future which completes when the message is successfully delivered.
   * Note that the future does not time out on its own and you must set it to false if it is not set when it goes out of
   * scope.
   */
  private SettableFuture<Boolean> writeMessage(final Device destination, String message) {
    final SettableFuture<Boolean> future = SettableFuture.create();
    final Writer writer = messageWriters.get(new Pair<>(destination.getNumber(), destination.getDeviceId()));
    if (writer != null) {
      try {
        writer.sendMessage(destination, future, message);
      } catch (IOException e) {
        disconnect(destination);
        future.set(false);
      }
    } else {
      disconnect(destination);
      future.set(false);
    }
    futureTimeoutExecutor.schedule(new Runnable() {
      @Override
      public void run() {
        future.set(false);
      }
    }, 10, TimeUnit.SECONDS);
    return future;
  }
}
