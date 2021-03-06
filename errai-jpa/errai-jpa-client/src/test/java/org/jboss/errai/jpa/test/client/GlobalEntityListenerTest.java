package org.jboss.errai.jpa.test.client;


import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreRemove;
import javax.persistence.PreUpdate;

import org.jboss.errai.ioc.client.Container;
import org.jboss.errai.ioc.client.container.IOCBeanManagerLifecycle;
import org.jboss.errai.jpa.client.local.ErraiEntityManager;
import org.jboss.errai.jpa.client.shared.GlobalEntityListener;
import org.jboss.errai.jpa.test.entity.Album;
import org.jboss.errai.jpa.test.entity.Artist;
import org.jboss.errai.jpa.test.entity.CallbackLogEntry;
import org.jboss.errai.jpa.test.entity.TestingGlobalEntityListener;
import org.jboss.errai.jpa.test.entity.Zentity;

import com.google.gwt.junit.client.GWTTestCase;

/**
 * Tests the client-side {@link GlobalEntityListener} annotation.
 *
 * @author Jonathan Fuerth <jfuerth@gmail.com>
 */
public class GlobalEntityListenerTest extends GWTTestCase {

  @Override
  public String getModuleName() {
    return "org.jboss.errai.jpa.test.JpaTest";
  }

  protected EntityManager getEntityManager() {
    JpaTestClient testClient = JpaTestClient.INSTANCE;
    assertNotNull(testClient);
    assertNotNull(testClient.entityManager);
    ((ErraiEntityManager) JpaTestClient.INSTANCE.entityManager).removeAll();
    return JpaTestClient.INSTANCE.entityManager;
  }

  @Override
  protected void gwtSetUp() throws Exception {
    super.gwtSetUp();

    TestingGlobalEntityListener.CALLBACK_LOG.clear();

    new IOCBeanManagerLifecycle().resetBeanManager();

    // We need to bootstrap the IoC container manually because GWTTestCase
    // doesn't call onModuleLoad() for us.
    new Container().bootstrapContainer();
  }

  public void testStoreAndFetchAlbumLifecycle() throws Exception {

    assertTrue(TestingGlobalEntityListener.CALLBACK_LOG.isEmpty());

    // make it
    Album album = new Album();
    album.setArtist(null);
    album.setName("Abbey Road");
    album.setReleaseDate(new Date(-8366400000L));

    // store it
    EntityManager em = getEntityManager();
    em.persist(album);
    em.flush();
    em.detach(album);

    List<CallbackLogEntry> expectedLifecycle = new ArrayList<CallbackLogEntry>();
    expectedLifecycle.add(new CallbackLogEntry(album, PrePersist.class));
    expectedLifecycle.add(new CallbackLogEntry(album, PostPersist.class));
    assertEquals(expectedLifecycle, TestingGlobalEntityListener.CALLBACK_LOG);

    // fetch a fresh copy
    Album fetchedAlbum = em.find(Album.class, album.getId());
    expectedLifecycle.add(new CallbackLogEntry(fetchedAlbum, PostLoad.class));
    assertEquals(expectedLifecycle, TestingGlobalEntityListener.CALLBACK_LOG);

    // fetch again; expect no more PostLoad notifications
    Album fetchedAlbum2 = em.find(Album.class, album.getId());
    assertSame(fetchedAlbum, fetchedAlbum2);
    assertEquals(expectedLifecycle, TestingGlobalEntityListener.CALLBACK_LOG);

  }

  public void testMultipleEntityTypesLifecycle() throws Exception {

    assertTrue(TestingGlobalEntityListener.CALLBACK_LOG.isEmpty());

    // make them
    Zentity zentity = new Zentity();

    Album album = new Album();
    album.setArtist(null);
    album.setName("Abbey Road");
    album.setReleaseDate(new Date(-8366400000L));

    // store them
    EntityManager em = getEntityManager();
    em.persist(zentity);
    em.persist(album);
    em.flush();
    em.clear();

    List<CallbackLogEntry> expectedLifecycle = new ArrayList<CallbackLogEntry>();
    expectedLifecycle.add(new CallbackLogEntry(zentity, PrePersist.class));
    expectedLifecycle.add(new CallbackLogEntry(zentity, PostPersist.class));
    expectedLifecycle.add(new CallbackLogEntry(album, PrePersist.class));
    expectedLifecycle.add(new CallbackLogEntry(album, PostPersist.class));
    assertEquals(expectedLifecycle, TestingGlobalEntityListener.CALLBACK_LOG);

    // fetch a fresh copy
    Album fetchedAlbum = em.find(Album.class, album.getId());
    expectedLifecycle.add(new CallbackLogEntry(fetchedAlbum, PostLoad.class));
    assertEquals(expectedLifecycle, TestingGlobalEntityListener.CALLBACK_LOG);

    // fetch again; expect no more PostLoad notifications
    Album fetchedAlbum2 = em.find(Album.class, album.getId());
    assertSame(fetchedAlbum, fetchedAlbum2);
    assertEquals(expectedLifecycle, TestingGlobalEntityListener.CALLBACK_LOG);

  }

  public void testRemoveEntityLifecycle() throws Exception {

    assertTrue(TestingGlobalEntityListener.CALLBACK_LOG.isEmpty());

    // make it
    Album album = new Album();
    album.setArtist(null);
    album.setName("Abbey Road");
    album.setReleaseDate(new Date(-8366400000L));

    // store it
    EntityManager em = getEntityManager();
    em.persist(album);
    em.flush();
    em.detach(album);

    List<CallbackLogEntry> expectedLifecycle = new ArrayList<CallbackLogEntry>();
    expectedLifecycle.add(new CallbackLogEntry(album, PrePersist.class));
    expectedLifecycle.add(new CallbackLogEntry(album, PostPersist.class));
    assertEquals(expectedLifecycle, TestingGlobalEntityListener.CALLBACK_LOG);

    // fetch a fresh copy
    Album fetchedAlbum = em.find(Album.class, album.getId());
    expectedLifecycle.add(new CallbackLogEntry(fetchedAlbum, PostLoad.class));
    assertEquals(expectedLifecycle, TestingGlobalEntityListener.CALLBACK_LOG);

    // delete it
    em.remove(fetchedAlbum);
    em.flush();
    expectedLifecycle.add(new CallbackLogEntry(fetchedAlbum, PreRemove.class));
    expectedLifecycle.add(new CallbackLogEntry(fetchedAlbum, PostRemove.class));
    assertEquals(expectedLifecycle, TestingGlobalEntityListener.CALLBACK_LOG);
  }

  public void testUpdateEntityLifecycle() throws Exception {

    assertTrue(TestingGlobalEntityListener.CALLBACK_LOG.isEmpty());

    // make it
    Album album = new Album();
    album.setArtist(null);
    album.setName("Abbey Road");
    album.setReleaseDate(new Date(-8366400000L));

    // store it
    EntityManager em = getEntityManager();
    em.persist(album);
    em.flush();

    List<CallbackLogEntry> expectedLifecycle = new ArrayList<CallbackLogEntry>();

    expectedLifecycle.add(new CallbackLogEntry(album, PrePersist.class));
    expectedLifecycle.add(new CallbackLogEntry(album, PostPersist.class));
    assertEquals(expectedLifecycle, TestingGlobalEntityListener.CALLBACK_LOG);

    // modify it
    album.setName("Cowabunga");
    em.flush();

    expectedLifecycle.add(new CallbackLogEntry(album, PreUpdate.class));
    expectedLifecycle.add(new CallbackLogEntry(album, PostUpdate.class));
    assertEquals(expectedLifecycle, TestingGlobalEntityListener.CALLBACK_LOG);
  }

  /**
   * Regression test for ERRAI-611.
   */
  public void testNoEventFromIdGeneratorProbe() throws Exception {

    // create an album with an artist, which we will probe for with the NO_SIDE_EFFECTS option

    Artist artist = new Artist();
    artist.setId(123L);
    artist.setName("The Beatles");

    Album album = new Album();
    album.setArtist(artist);
    album.setName("Abbey Road");
    album.setReleaseDate(new Date(-8366400000L));

    // store them
    EntityManager em = getEntityManager();
    em.persist(artist);
    em.persist(album);
    em.flush();
    em.clear();

    TestingGlobalEntityListener.CALLBACK_LOG.clear();

    ErraiEntityManager eem = (ErraiEntityManager) em;
    assertTrue(eem.backendContains(eem.keyFor(album)));

    // Finally, ensure there were no events fired as a result of the probe
    // (originally, we were getting a PostLoad for artist, because it was being cascade-fetched from album)
    assertEquals("[]", TestingGlobalEntityListener.CALLBACK_LOG.toString());
  }
}
