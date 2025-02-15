/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.kubernetes.client.extended.leaderelection.resourcelock;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConfigMapLockTest {

  private KubernetesClient kc;

  @BeforeEach
  void setUp() {
    kc = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
  }

  @Test
  void missingNamespaceShouldThrowException() {
    // Given
    final Executable newInstance = () -> new ConfigMapLock(null, "name", "1337");
    // When - Then
    assertThrows(NullPointerException.class, newInstance);
  }

  @Test
  void missingNameShouldThrowException() {
    // Given
    final Executable newInstance = () -> new ConfigMapLock("namespace", null, "1337");
    // When - Then
    assertThrows(NullPointerException.class, newInstance);
  }

  @Test
  void missingIdentityShouldThrowException() {
    // Given
    final Executable newInstance = () -> new ConfigMapLock("namespace", "name", null);
    // When - Then
    assertThrows(NullPointerException.class, newInstance);
  }

  @Test
  void getWithExistingConfigMapShouldReturnLeaderElectionRecord() {
    // Given
    final ConfigMap cm = new ConfigMap();
    cm.setMetadata(new ObjectMetaBuilder()
        .withAnnotations(
            Collections.singletonMap("control-plane.alpha.kubernetes.io/leader",
                "{\"holderIdentity\":\"1337\",\"leaseDuration\":15,\"acquireTime\":1445401740,\"renewTime\":1445412480}"))
        .withResourceVersion("313373").build());
    final ConfigMapLock lock = new ConfigMapLock("namespace", "name", "1337");
    // When
    final LeaderElectionRecord result = lock.toRecordInternal(cm);
    // Then
    assertNotNull(result);
    assertEquals("313373", result.getVersion());
    assertEquals("1337", result.getHolderIdentity());
    assertEquals(15, result.getLeaseDuration().getSeconds());
    assertEquals(ZonedDateTime.of(2015, 10, 21, 4, 29, 0, 0, ZoneId.of("UTC")), result.getAcquireTime());
  }

  @Test
  void createWithValidLeaderElectionRecordShouldSendPostRequest() throws Exception {
    // Given
    final LeaderElectionRecord record = new LeaderElectionRecord(
        "1", Duration.ofSeconds(1), ZonedDateTime.now(), ZonedDateTime.now(), 0);
    final ConfigMapLock lock = new ConfigMapLock("namespace", "name", "1337");
    // When
    lock.create(kc, record);
    // Then
    verify(kc.resource(any(ConfigMap.class))).create();
  }

  @Test
  void updateWithValidLeaderElectionRecordShouldSendPatchRequest() throws Exception {
    // Given
    final LeaderElectionRecord record = new LeaderElectionRecord(
        "1337", Duration.ofSeconds(1), ZonedDateTime.now(), ZonedDateTime.now(), 0);
    record.setVersion("313373");
    final ConfigMapLock lock = new ConfigMapLock("namespace", "name", "1337");
    // When
    lock.update(kc, record);
    // Then
    verify(kc.resource(any(ConfigMap.class))).patch(any(PatchContext.class));
  }

  @Test
  void identityShouldReturnProvidedIdentity() {
    // Given
    final ConfigMapLock configMapLock = new ConfigMapLock("namespace", "name", "1337");
    // When
    final String identity = configMapLock.identity();
    // Then
    assertEquals("1337", identity);
  }

  @Test
  void describeShouldReturnAnIdentifiableDescription() {
    // Given
    final ConfigMapLock configMapLock = new ConfigMapLock("namespace", "name", "1337");
    // When
    final String description = configMapLock.describe();
    // Then
    assertEquals("ConfigMapLock: namespace - name (1337)", description);
  }
}
