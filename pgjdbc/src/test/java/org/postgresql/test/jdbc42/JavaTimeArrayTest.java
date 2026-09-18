/*
 * Copyright (c) 2026, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.test.jdbc42;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.postgresql.test.jdbc2.BaseTest4;

import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class JavaTimeArrayTest extends BaseTest4 {

  @Test
  public void localDateArrayUsesScalarFormatting() throws SQLException {
    Object[] values = {
        LocalDate.of(-500, 1, 1),
        LocalDate.of(10000, 1, 1),
        LocalDate.MAX
    };
    assertArrayMatchesScalar("date", values);
  }

  @Test
  public void localDateTimeArrayUsesScalarFormatting() throws SQLException {
    Object[] values = {
        LocalDateTime.of(-500, 1, 1, 2, 3),
        LocalDateTime.of(10000, 1, 1, 2, 3),
        LocalDateTime.MAX
    };
    assertArrayMatchesScalar("timestamp", values);
  }

  @Test
  public void offsetDateTimeArrayUsesScalarFormatting() throws SQLException {
    Object[] values = {
        OffsetDateTime.of(-500, 1, 1, 2, 3, 0, 0, ZoneOffset.UTC),
        OffsetDateTime.of(10000, 1, 1, 2, 3, 0, 0, ZoneOffset.UTC),
        OffsetDateTime.MAX
    };
    assertArrayMatchesScalar("timestamptz", values);
  }

  private void assertArrayMatchesScalar(String typeName, Object[] values) throws SQLException {
    for (Object value : values) {
      assertEquals(scalarText(typeName, value), arrayText(typeName, value), value.toString());
    }
  }

  private String scalarText(String typeName, Object value) throws SQLException {
    try (PreparedStatement ps = con.prepareStatement("SELECT CAST(? AS " + typeName + ")::text")) {
      ps.setObject(1, value);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getString(1);
      }
    }
  }

  private String arrayText(String typeName, Object value) throws SQLException {
    Array array = con.createArrayOf(typeName, new Object[]{value});
    try {
      try (PreparedStatement ps = con.prepareStatement(
          "SELECT (CAST(? AS " + typeName + "[]))[1]::text")) {
        ps.setArray(1, array);
        try (ResultSet rs = ps.executeQuery()) {
          rs.next();
          return rs.getString(1);
        }
      }
    } finally {
      array.free();
    }
  }
}
