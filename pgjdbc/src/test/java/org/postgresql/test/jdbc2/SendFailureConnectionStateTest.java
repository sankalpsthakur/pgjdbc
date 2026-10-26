/*
 * Copyright (c) 2026, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.test.jdbc2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.postgresql.jdbc.PreferQueryMode;
import org.postgresql.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A query or batch entry that fails while sending must not leave the connection open and out of
 * sync. After the failure, either {@link java.sql.Connection#isClosed()} is true or a later
 * {@code SELECT 42} still returns 42. See
 * <a href="https://github.com/pgjdbc/pgjdbc/issues/4504">issue #4504</a>.
 */
@ParameterizedClass
@MethodSource("data")
public class SendFailureConnectionStateTest extends BaseTest4 {

  public SendFailureConnectionStateTest(PreferQueryMode preferQueryMode) {
    setPreferQueryMode(preferQueryMode);
  }

  public static Iterable<Object[]> data() {
    Collection<Object[]> ids = new ArrayList<>();
    for (PreferQueryMode preferQueryMode : PreferQueryMode.values()) {
      ids.add(new Object[]{preferQueryMode});
    }
    return ids;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestUtil.createTempTable(con, "probe_t", "b bytea");
  }

  @Test
  void batchEntryThatFailsWhileSendingDoesNotLeaveTheConnectionOutOfSync() throws SQLException {
    try (PreparedStatement ps = con.prepareStatement("INSERT INTO probe_t VALUES (?)")) {
      ps.setBinaryStream(1, new ByteArrayInputStream(new byte[]{1, 2}), 2);
      ps.addBatch();
      ps.setBinaryStream(1, failingBinaryStream(), 2);
      ps.addBatch();
      assertThrows(SQLException.class, ps::executeBatch);
    }
    if (con.isClosed()) {
      return;
    }
    try (Statement st = con.createStatement();
         ResultSet rs = st.executeQuery("SELECT 42")) {
      assertTrue(rs.next(), "an open connection must return a row for SELECT 42");
      assertEquals(42, rs.getInt(1),
          "a connection that stays open after a failed batch must still be in sync");
    }
  }

  @Test
  void brokenBinaryStreamOnASingleStatementIsRestartable() throws SQLException {
    SQLException failure = assertThrows(SQLException.class, () -> {
      try (PreparedStatement ps = con.prepareStatement("INSERT INTO probe_t VALUES (?)")) {
        ps.setBinaryStream(1, failingBinaryStream(), 2);
        ps.executeUpdate();
      }
    });
    assertFalse(con.isClosed(),
        () -> "a broken stream on a single statement must not close the connection: " + failure);
    try (PreparedStatement ps = con.prepareStatement("INSERT INTO probe_t VALUES (?)")) {
      ps.setBinaryStream(1, new ByteArrayInputStream(new byte[]{1, 2}), 2);
      assertEquals(1, ps.executeUpdate());
    }
    try (Statement st = con.createStatement();
         ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM probe_t")) {
      assertTrue(rs.next());
      assertEquals(1, rs.getInt(1));
    }
  }

  private static InputStream failingBinaryStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }
    };
  }
}
