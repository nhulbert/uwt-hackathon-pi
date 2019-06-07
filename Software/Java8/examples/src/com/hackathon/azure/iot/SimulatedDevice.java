// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

// This application uses the Azure IoT Hub device SDK for Java
// For samples see: https://github.com/Azure/azure-iot-sdk-java/tree/master/device/iot-device-samples

package com.hackathon.azure.iot;

import com.microsoft.azure.sdk.iot.device.*;
import com.google.gson.Gson;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import org.iot.raspberry.grovepi.GrovePi;
import org.iot.raspberry.grovepi.pi4j.GrovePi4J;
import org.iot.raspberry.grovepi.devices.GroveTemperatureAndHumiditySensor;
import java.io.IOException;

public class SimulatedDevice {
  // The device connection string to authenticate the device with your IoT hub.
  // Using the Azure CLI:
  // az iot hub device-identity show-connection-string --hub-name {YourIoTHubName} --device-id MyJavaDevice --output table
  private static String connString;
  
  // Using the MQTT protocol to connect to IoT Hub
  private static IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;
  private static DeviceClient client;

  private static GrovePi grovePi;

  private static final int WHICHDUMMY = 1;
  private static final String location = "25,13";

  static{
      switch(WHICHDUMMY){
          case 0:
              connString = "HostName=uwt-rasppi.azure-devices.net;DeviceId=MyJavaDevice;SharedAccessKey=MYC7pLyU8R01VQuJ3SrO8Gw3mKOVbvnS7ThUu8AV7Hw=";              
              break;
          case 1:
              connString = "HostName=uwt-rasppi.azure-devices.net;DeviceId=MyJavaDevice2;SharedAccessKey=9VZBQPI1ztZdoPirsOBFjpxI0n1SReTj6NHc1fnAXzc=";
      }
      
      try {
        grovePi = new GrovePi4J();
      } catch (IOException e){
          System.out.println("Can't connect to Raspberry pi");
      }
  }
  
  // Specify the telemetry to send to your IoT hub.
  private static class TelemetryDataPoint {
    public double temperature;
    public double humidity;
    public String location;
    
    // Serialize object to JSON format.
    public String serialize() {
      Gson gson = new Gson();
      return gson.toJson(this);
    }
  }

  // Print the acknowledgement received from IoT Hub for the telemetry message sent.
  private static class EventCallback implements IotHubEventCallback {
    public void execute(IotHubStatusCode status, Object context) {
      System.out.println("IoT Hub responded to message with status: " + status.name());

      if (context != null) {
        synchronized (context) {
          context.notify();
        }
      }
    }
  }

  private static class MessageSender implements Runnable {
    public void run() {
      Random r = new Random();  
        
      try {
        GroveTemperatureAndHumiditySensor dht;
        if (WHICHDUMMY == 0) {
            dht = new GroveTemperatureAndHumiditySensor(grovePi, 8, GroveTemperatureAndHumiditySensor.Type.DHT11);
        }        
// Initialize the simulated telemetry.
        double minTemperature = 20;
        double minHumidity = 60;
        Random rand = new Random();

        while (true) {
          // Simulate telemetry.
          Double currentTemperature = -1.0;
          Double currentHumidity = -1.0;
          if (WHICHDUMMY == 0){
            try {
                Double newTemp = dht.get().getTemperature();
                Double newHumidity = dht.get().getHumidity();
                
                if (!newTemp.isNaN()) {
                    currentTemperature = newTemp;
                }

                if (newHumidity.isNaN()) {
                    currentHumidity = newHumidity;
                }

              } catch (IOException e){
                  System.out.println("Error measuring temp/humid");
              }
          } else {
              currentTemperature = 17+3*r.nextGaussian();
          }
          
          try {
            TelemetryDataPoint telemetryDataPoint = new TelemetryDataPoint();
            telemetryDataPoint.temperature = currentTemperature;
            telemetryDataPoint.humidity = currentHumidity;
            telemetryDataPoint.location = location;
            
            // Add the telemetry to the message body as JSON.
            String msgStr = telemetryDataPoint.serialize();
            Message msg = new Message(msgStr);

            System.out.println("Sending message: " + msgStr);

            Object lockobj = new Object();

            // Send the message.
            EventCallback callback = new EventCallback();
            client.sendEventAsync(msg, callback, lockobj);

            synchronized (lockobj) {
              lockobj.wait();
            }
        } catch (IllegalArgumentException e) {
            System.out.println("measurement invalid");
        }
        
        Thread.sleep(1000);
        
        }
      } catch (InterruptedException e) {
        System.out.println("Finished.");
      }
    }
  }

  public static void main(String[] args) throws IOException, URISyntaxException {

    // Connect to the IoT hub.
    client = new DeviceClient(connString, protocol);
    client.open();

    // Create new thread and start sending messages 
    MessageSender sender = new MessageSender();
    ExecutorService executor = Executors.newFixedThreadPool(1);
    executor.execute(sender);

    // Stop the application.
    System.out.println("Press ENTER to exit.");
    System.in.read();
    executor.shutdownNow();
    client.closeNow();
  }
}
