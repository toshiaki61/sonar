/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2012 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.server.filters;

import com.google.common.collect.Sets;
import org.junit.Test;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.resources.Resource;
import org.sonar.core.persistence.dialect.Derby;
import org.sonar.core.persistence.dialect.MsSql;
import org.sonar.jpa.test.AbstractDbUnitTestCase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

public class FilterExecutorTest extends AbstractDbUnitTestCase {

  @Test
  public void mustDefineAtLeastOneQualifier() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(new Filter());
    assertThat(result.size()).isEqualTo(0);// no qualifiers
  }

  @Test
  public void filterOnScopes() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setScopes(Sets.newHashSet(Resource.SCOPE_SPACE)));
    assertSnapshotIds(result, 4);
  }

  @Test
  public void filterOnQualifiers() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(new Filter().setQualifiers(Sets.newHashSet(Resource.QUALIFIER_PROJECT, Resource.QUALIFIER_MODULE)));
    assertSnapshotIds(result, 2, 3);
  }

  @Test
  public void filterOnLanguages() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setLanguages(Sets.newHashSet("java")));
    assertSnapshotIds(result, 2, 4);
  }

  @Test
  public void filterOnDate() throws ParseException {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2008-12-26 00:00");
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setDateCriterion(new DateCriterion(">", date)));
    assertSnapshotIds(result, 3);
  }

  @Test
  public void filterOnDateIncludesTime() throws ParseException {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2008-12-25 03:00");
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setDateCriterion(new DateCriterion("<", date)));
    assertSnapshotIds(result, 2, 4);
  }

  @Test
  public void filterOnBaseSnapshot() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setPath(2, 2, ""));
    assertSnapshotIds(result, 4);
  }

  @Test
  public void sortByName() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setSortedByName());
    assertSortedSnapshotIds(result, 2, 4, 3);
  }

  @Test
  public void sortByKey() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setSortedByKey());
    assertSortedSnapshotIds(result, 3, 2, 4);
  }

  @Test
  public void sortByDate() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setSortedByDate());
    assertSortedSnapshotIds(result, 2, 4, 3);
  }

  @Test
  public void sortByDescendingDate() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setSortedByDate().setAscendingSort(false));
    assertSortedSnapshotIds(result, 3, 4, 2);
  }

  @Test
  public void sortByAscendingDate() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setSortedByDate().setAscendingSort(true));
    assertSortedSnapshotIds(result, 2, 4, 3);
  }

  @Test
  public void sortByAscendingMeasureValue() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .setSortedMetricId(2, true, false);

    FilterResult result = executor.execute(filter);
    assertSortedSnapshotIds(result, 6, 5);
  }

  @Test
  public void sortByDecendingMeasureValue() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .setSortedMetricId(2, true, false)
        .setAscendingSort(false);

    FilterResult result = executor.execute(filter);
    assertSortedSnapshotIds(result, 5, 6);
  }

  @Test
  public void applySingleMeasureCriterion() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .addMeasureCriterion(new MeasureCriterion(2, ">", 50.0, false));

    FilterResult result = executor.execute(filter);
    assertSnapshotIds(result, 5);
  }

  @Test
  public void applyManyMeasureCriteria() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .addMeasureCriterion(new MeasureCriterion(2, ">", 50.0, false))
        .addMeasureCriterion(new MeasureCriterion(1, ">", 100.0, false));

    FilterResult result = executor.execute(filter);
    assertSnapshotIds(result, 5);
  }

  @Test
  public void criteriaAreExclusive() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .addMeasureCriterion(new MeasureCriterion(2, ">", 50.0, false))
        .addMeasureCriterion(new MeasureCriterion(1, "<", 100.0, false));

    FilterResult result = executor.execute(filter);
    assertThat(result.size()).isEqualTo(0);
  }

  @Test
  public void sortAndFilterMeasures() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .addMeasureCriterion(new MeasureCriterion(2, ">", 5.0, false))
        .addMeasureCriterion(new MeasureCriterion(1, ">", 5.0, false))
        .setSortedMetricId(2, true, false); // sort by coverage

    FilterResult result = executor.execute(filter);
    assertSnapshotIds(result, 6, 5);
  }

  @Test
  public void sortDescendingAndFilterMeasures() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .addMeasureCriterion(new MeasureCriterion(2, ">", 5.0, false)) // filter on coverage
        .addMeasureCriterion(new MeasureCriterion(1, ">", 5.0, false)) // filter on lines
        .setSortedMetricId(2, true, false) // sort by coverage
        .setAscendingSort(false);

    FilterResult result = executor.execute(filter);
    assertSnapshotIds(result, 5, 6);
  }

  @Test
  public void filterByResourceKey() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setKeyRegexp("*:org.sonar.*"));
    assertSnapshotIds(result, 4);
  }

  @Test
  public void filterByResourceKeyIsCaseInsensitive() {
    setupData("shared");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    FilterResult result = executor.execute(Filter.createForAllQualifiers().setKeyRegexp("*:ORG.SonAR.*"));
    assertSnapshotIds(result, 4);
  }

  @Test
  public void filterByMissingMeasureValue() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .addMeasureCriterion(new MeasureCriterion(3, ">", 0.0, false)); // filter on duplicated lines

    FilterResult result = executor.execute(filter);
    assertSnapshotIds(result, 6);
  }

  @Test
  public void filterByMissingMeasureValues() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .addMeasureCriterion(new MeasureCriterion(1, ">", 0.0, false)) // filter on lines
        .addMeasureCriterion(new MeasureCriterion(3, ">", 0.0, false)); // filter on duplicated lines

    FilterResult result = executor.execute(filter);
    assertSnapshotIds(result, 6);
  }

  @Test
  public void sortByMissingMeasureValue() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .setSortedMetricId(3, true, false); // sort by duplicated lines

    FilterResult result = executor.execute(filter);
    assertSnapshotIds(result, 5, 6);
  }

  @Test
  public void filterByMeasureValueAndSortOnOtherMetric() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .addMeasureCriterion(new MeasureCriterion(1, ">", 0.0, false)) // lines > 0
        .setSortedMetricId(2, true, false); // sort by coverage

    FilterResult result = executor.execute(filter);
    assertSnapshotIds(result, 6, 5);
  }

  @Test
  public void intersectionOfCriteriaOnSameMetric() {
    setupData("shared", "measures");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.CLASS))
        .addMeasureCriterion(new MeasureCriterion(1, ">", 400.0, false)) // lines > 400
        .addMeasureCriterion(new MeasureCriterion(1, "<", 600.0, false)); // lines > 400

    FilterResult result = executor.execute(filter);
    assertSnapshotIds(result, 5);
  }

  @Test
  public void ignoreProjectCopiesOfViews() {
    setupData("views");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setQualifiers(Sets.newHashSet(Qualifiers.PROJECT));

    FilterResult result = executor.execute(filter);
    assertSnapshotIds(result, 1); // the "project copy" with id 4 is ignored
  }

  @Test
  public void loadProjectCopiesIfPathIsAView() {
    setupData("views");
    FilterExecutor executor = new FilterExecutor(getSession(), new Derby());
    Filter filter = new Filter()
        .setPath(2, 2, "")
        .setQualifiers(Sets.newHashSet(Qualifiers.SUBVIEW, Qualifiers.PROJECT));

    FilterResult result = executor.execute(filter);
    assertSnapshotIds(result, 3, 4);
  }

  @Test
  public void explicitelyUseIndexOnMsSql() {
    Filter filter = new Filter().addMeasureCriterion(new MeasureCriterion(1, ">", 400.0, false));

    String sql = new FilterExecutor(getSession(), new MsSql()).toSql(filter);
    assertThat(sql).contains(" WITH (INDEX(measures_sid_metric)) ");

    sql = new FilterExecutor(getSession(), new Derby()).toSql(filter);
    assertThat(sql).doesNotContain(" WITH (INDEX(measures_sid_metric)) ");
  }


  private void assertSnapshotIds(FilterResult result, int... snapshotIds) {
    assertThat(result.size()).isEqualTo(snapshotIds.length);
    for (int snapshotId : snapshotIds) {
      boolean found = false;
      for (Object[] row : result.getRows()) {
        found |= result.getSnapshotId(row) == snapshotId;
      }
      if (!found) {
        fail("Snapshot id not found in results: " + snapshotId);
      }
    }
  }

  private void assertSortedSnapshotIds(FilterResult result, int... snapshotIds) {
    assertThat(result.size()).isEqualTo(snapshotIds.length);
    for (int index = 0; index < snapshotIds.length; index++) {
      assertThat(result.getSnapshotId(result.getRows().get(index))).isEqualTo(snapshotIds[index]);
    }
  }
}
