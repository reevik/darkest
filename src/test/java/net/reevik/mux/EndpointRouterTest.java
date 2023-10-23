/*
 * Copyright (c) 2023 Erhan Bagdemir. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.reevik.mux;

import static net.reevik.mux.MonitorableAssertionFactory.expectSuccess;
import static net.reevik.mux.validators.ValidatorFactory.mustEqual;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import net.reevik.mux.RoutingConfiguration.Builder;
import net.reevik.mux.validators.EqualsAcceptanceImpl;
import net.reevik.mux.validators.ResultValidator;
import net.reevik.mux.validators.ResultValidatorImpl;
import net.reevik.mux.validators.ValidationResult;
import net.reevik.mux.validators.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EndpointRouterTest {

  private final ResultValidator<String> alwaysTrueValidator =
      (resultA, resultB) -> ValidatorFactory.newResult(true, "N/A");

  @Test
  public void testNonRouting() throws Exception {
    CountingCriterion countingCriterion = new CountingCriterion(1);
    ResultValidator<String> mustEqual = spy(mustEqual());
    RoutingConfiguration<String> routingConfiguration = Builder.<String>create()
        .withSideA(() -> "A")
        .withSideB(EndpointRouterTest::assertNotCalled)
        .withExecutorService(new DummyExecutor())
        .withResultValidator(mustEqual)
        .withRoutingCriterion(countingCriterion)
        .withRoutingMode(RoutingMode.A_SIDE)
        .build();

    EndpointRouter<String> router = new EndpointRouter<>(routingConfiguration);
    String result = router.route();
    assertThat(result).isEqualTo("A");
    verify(mustEqual, never()).validate(any(), any());
  }

  private static String assertNotCalled() {
    throw new AssertionError();
  }

  @Test
  public void testAlwaysRouting() throws Exception {
    CountingCriterion countingCriterion = new CountingCriterion(1);
    RoutingConfiguration<String> routingConfiguration = Builder.<String>create()
        .withSideA(EndpointRouterTest::assertNotCalled)
        .withSideB(() -> "B")
        .withExecutorService(new DummyExecutor())
        .withResultValidator(mustEqual())
        .withRoutingCriterion(countingCriterion)
        .withRoutingMode(RoutingMode.B_SIDE)
        .build();

    EndpointRouter<String> router = new EndpointRouter<>(routingConfiguration);
    String result = router.route();
    assertThat(result).isEqualTo("B");
  }

  @Test
  public void testShadowTestingPassing() throws Exception {
    CountingCriterion countingCriterion = new CountingCriterion(1);
    RoutingConfiguration<String> routingConfiguration = Builder.<String>create()
        .withSideA(() -> "abc")
        .withSideB(() -> "abc")
        .withExecutorService(new DummyExecutor())
        .withResultValidator(mustEqual())
        .withRoutingCriterion(countingCriterion)
        .withMonitorable(expectSuccess())
        .withRoutingMode(RoutingMode.SHADOW_MODE_ACTIVE)
        .build();

    EndpointRouter<String> router = new EndpointRouter<>(routingConfiguration);
    assertThat(router.route()).isEqualTo("abc");
  }

  @Test
  public void testShadowTestingPassingWithResultFromBSide() throws Exception {
    CountingCriterion countingCriterion = new CountingCriterion(1);
    RoutingConfiguration<String> routingConfiguration = Builder.<String>create()
        .withSideA(() -> "abc")
        .withSideB(() -> "dbc")
        .withExecutorService(new DummyExecutor())
        .withResultValidator(alwaysTrueValidator)
        .withRoutingCriterion(countingCriterion)
        .withMonitorable(expectSuccess())
        .withRoutingMode(RoutingMode.SHADOW_MODE_ACTIVE)
        .build();

    EndpointRouter<String> router = new EndpointRouter<>(routingConfiguration);
    assertThat(router.route()).isEqualTo("dbc");
  }

  @Test
  public void testShadowTestingPassingWithResultFromASide() throws Exception {
    CountingCriterion countingCriterion = new CountingCriterion(1);
    RoutingConfiguration<String> routingConfiguration = Builder.<String>create()
        .withSideA(() -> "abc")
        .withSideB(() -> "dbc")
        .withExecutorService(new DummyExecutor())
        .withResultValidator(alwaysTrueValidator)
        .withRoutingCriterion(countingCriterion)
        .withMonitorable(expectSuccess())
        .withRoutingMode(RoutingMode.SHADOW_MODE_PASSIVE)
        .build();

    EndpointRouter<String> router = new EndpointRouter<>(routingConfiguration);
    assertThat(router.route()).isEqualTo("abc");
  }

  @Test
  public void testShadowTestingValidationFailure() throws Exception {
    ResultValidatorImpl<String> validator = spy(new ResultValidatorImpl<>(
        Collections.singletonList(new EqualsAcceptanceImpl<>())));
    CountingCriterion countingCriterion = new CountingCriterion(1);
    RoutingConfiguration<String> routingConfiguration = Builder.<String>create()
        .withSideA(() -> "abc")
        .withSideB(() -> "abd")
        .withExecutorService(new DummyExecutor())
        .withResultValidator(validator)
        .withRoutingCriterion(countingCriterion)
        .withRoutingMode(RoutingMode.SHADOW_MODE_ACTIVE)
        .build();

    ValidatorResultCapture<ValidationResult> capture = new ValidatorResultCapture<>();
    doAnswer(capture).when(validator).validate(any(), any());
    EndpointRouter<String> router = new EndpointRouter<>(routingConfiguration);
    assertThat(router.route()).isEqualTo("abc");
    assertThat(capture.getResult().isPassed()).isFalse();
  }
}