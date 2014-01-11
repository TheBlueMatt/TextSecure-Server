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

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.whispersystems.textsecuregcm.util.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface StoredMessages {

  @SqlUpdate("INSERT INTO stored_messages (destination_id, encrypted_message) VALUES (:destination_id, :encrypted_message)")
  void insert(@Bind("destination_id") long destinationAccountId, @Bind("encrypted_message") String encryptedOutgoingMessage);

  @Mapper(StoredMessageMapper.class)
  @SqlQuery("SELECT id, encrypted_message FROM stored_messages WHERE destination_id = :account_id")
  List<Pair<Long, String>> getMessagesForAccountId(@Bind("account_id") long accountId);

  @SqlUpdate("DELETE FROM stored_messages WHERE id = :id")
  void removeStoredMessage(@Bind("id") long id);

  public static class StoredMessageMapper implements ResultSetMapper<Pair<Long, String>> {
    @Override
    public Pair<Long, String> map(int i, ResultSet resultSet, StatementContext statementContext)
        throws SQLException
    {
      return new Pair<>(resultSet.getLong("id"), resultSet.getString("encrypted_message"));
    }
  }}
