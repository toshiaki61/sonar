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
package org.sonar.check;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class CheckTest {
  @Test
  public void getCheckMetadata() {
    Check check = FakeCheck.class.getAnnotation(Check.class);
    assertThat(check.title(), is("Detailed Check"));
    assertThat(check.description(), is("Detailed description"));
  }

  @Test
  public void getCheckProperty() throws NoSuchFieldException {
    Field field = FakeCheck.class.getDeclaredField("max");
    CheckProperty propertyAnnotation = field.getAnnotation(CheckProperty.class);
    assertThat(propertyAnnotation.description(), is("Maximum value"));
  }
}
