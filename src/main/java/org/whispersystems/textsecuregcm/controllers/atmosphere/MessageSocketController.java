package org.whispersystems.textsecuregcm.controllers.atmosphere;

import com.google.common.base.Optional;
import com.yammer.dropwizard.auth.AuthenticationException;
import com.yammer.dropwizard.auth.basic.BasicCredentials;
import org.atmosphere.annotation.Suspend;
import org.atmosphere.config.service.AtmosphereService;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.jersey.JerseyBroadcaster;
import org.whispersystems.textsecuregcm.auth.DeviceAuthenticator;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.StoredMessageManager;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("")
@AtmosphereService(broadcaster = JerseyBroadcaster.class)
public class MessageSocketController {
  public static StoredMessageManager storedMessageManager;
  public static DeviceAuthenticator deviceAuthenticator;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Suspend
  public void openSocket(@QueryParam("user") String user, @QueryParam("password") String password,
                         @Context AtmosphereResource resource)
  {
    try {
      if (user == null || password == null)
        throw new AuthenticationException("user/password not specified");
      Optional<Device> device = deviceAuthenticator.authenticate(new BasicCredentials(user, password));
      if (!device.isPresent())
        throw new AuthenticationException("Couldn't get Account from user/password combo");

      System.out.println("opening socket..." + (resource == null ? "null" : (resource.uuid() == null ? "null uuid" : resource.uuid()))
          + " for account " + device.get().getId());
      storedMessageManager.registerListener(device.get(), resource);
    } catch (AuthenticationException e) {
      System.out.println("Bad auth");
      e.printStackTrace();
      //try { resource.close(); } catch (IOException e1) { } TODO
      throw new WebApplicationException(Response.status(401).build());
    }
  }

  @PUT
  @Path("/{id}")
  public void ack(@Context HttpServletRequest req, @PathParam("id") long id) {
    Device device = null;
    try {
      device = deviceAuthenticator.authenticate(req);
    } catch (AuthenticationException e) {
      throw new WebApplicationException(Response.status(401).build());
    }
    storedMessageManager.receivedAck(device, id);
  }
}
