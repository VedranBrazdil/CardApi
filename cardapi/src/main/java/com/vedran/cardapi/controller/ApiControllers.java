package com.vedran.cardapi.controller;


import com.vedran.cardapi.models.Client;
import com.vedran.cardapi.repo.ClientRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Scanner;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping(path="/")
public class ApiControllers {

    final String pathOfFolder = "StartedCardMakingProcesses";
    final String fileDataDelimiter = ":";
    final String fileNameDelimiter = "--";
    final File dirOfStartedCardMakingProcesses = new File(pathOfFolder);

    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss");
    @Autowired
    private ClientRepo clientRepo;

    @GetMapping(path = "help")
    public @ResponseBody String getStarted() {
        return "Welcome to CARD Request API"
                + "\n"
                + "\nUse GET /help to get list of available apis."
                + "\nUse GET /clients to retrieve all requests by all Clients."
                + "\nFeel free to use query params (oib, lastName, firstName or status) for filtering."
                + "\n"
                + "\nUse POST /client/card to add new Client request for card."
                + "\nUse GET /client/{oib} to retrieve data of all Client requests."
                + "\nUse GET /client/card/{id} to retrieve data of that specific requests."
                + "\nUse PATCH /client/card/{id} to update that specific request."
                + "\nUse PUT /client/card/{id} to overwrite that specific request."
                + "\nUse DELETE /client/{oib} to delete all requests of that Client."
                + "\nUse DELETE /client/card/{id} to delete that specific request."
                + "\n"
                + "\nUse POST /client/card/{id}/startProcess to start the creating card process."
                + "\nUse DELETE /client/card/{id}/stopProcess to stop the creating card process."
                + "\nUse DELETE /clients/killProcess to stop all requests by Clients.";
    }

    @GetMapping(path = "clients")
    public @ResponseBody Iterable<Client> getMeAllClients(
            @RequestParam(name = "lastName", required = false) String lastName,
            @RequestParam(name = "firstName", required = false) String firstName,
            @RequestParam(name = "status", required = false) Client.Status status,
            @RequestParam(name = "oib", required = false) Long oib
    ) {
        // Check for all queries:
        Client example = new Client();
        boolean flagAtLeastSomething = false;
        if (firstName != null) {
            flagAtLeastSomething = true;
            example.setFirstName(firstName);
        }
        if (lastName != null) {
            flagAtLeastSomething = true;
            example.setLastName(lastName);
        }
        if (status != null) {
            flagAtLeastSomething = true;
            example.setStatus(status);
        }
        if (oib != null && checkOib(oib)) {
            flagAtLeastSomething = true;
            example.setOib(oib);
        }
        if (flagAtLeastSomething) return clientRepo.findAll(Example.of(example));

        //Else: Default - Get all
        return clientRepo.findAll();
    }

    @GetMapping(path = "client/{oib}")
    public @ResponseBody Object getMeClient(@PathVariable Long oib) {
        if (checkOib(oib)) {
            return clientRepo.findByOib(oib);
            // Will return Empty List if there is none
        }
        return "ERROR: Invalid OIB: " + oib;
    }

    @GetMapping(path = "client/card/{id}")
    public @ResponseBody Object getMeClientRequest(@PathVariable Long id) {
        if (id != null && id > 0) {
            try {
                Client thisClient = clientRepo.findById(id).get();
                return thisClient;
            } catch (Exception e) {
                // Missing Client Request
                return "ERROR: Client request under ID: " + id + " does not exist.";
            }
        }

        return "ERROR: Invalid ID: " + id;
    }

    @PostMapping(path = "client/card")
    public @ResponseBody Object addClientRequest(@RequestBody Client client) {
        List<String> missingDataList = checkIfDataOk_Client(client);
        if (!missingDataList.isEmpty()) {
            String missingData = "";
            boolean flagFirst = true;
            for (String mData : missingDataList) {
                if (flagFirst) {
                    missingData = mData;
                    flagFirst = false;
                } else {
                    missingData = missingData + ", " + mData;
                }
            }
            return "ERROR: Client request not created. Missing data: " + missingData;
        }
        client.setStatus(Client.Status.REQUESTED); // init status
        clientRepo.save(client);
        return client;
    }

    @PutMapping(path = "client/card/{id}")
    public @ResponseBody Object overwriteClientRequest(@PathVariable Long id, @RequestBody Client client) {
        List<String> missingDataList = checkIfDataOk_Client(client);
        if (!missingDataList.isEmpty()) {
            String missingData = "";
            boolean flagFirst = true;
            for (String mData : missingDataList) {
                if (flagFirst) {
                    missingData = mData;
                    flagFirst = false;
                } else {
                    missingData = missingData + ", " + mData;
                }
            }
            return "ERROR: Client request not created/updated. Missing data: " + missingData;
        }
        if (id != null && id > 0) {
            // Everything OK -> Update process and then save
            client.setId(id);
            try {
                Client thisOldClient = clientRepo.findById(id).get();
                client.setStatus(thisOldClient.getStatus()); // Get existing status
                if (!(Client.Status.REQUESTED == thisOldClient.getStatus())) {
                    //Update if process started/inactive:
                    updateProcess(client, thisOldClient.getOib());
                }
            } catch (Exception e) {
                // There was no client on this ID.
                client.setStatus(Client.Status.REQUESTED); // init status
            }

            // If new ID value is given:
            Client newValue = clientRepo.save(client);

            return newValue;
        }

        return "ERROR: Client request not created/updated. Missing data: id";
    }


    @PatchMapping(path = "client/card/{id}")
    public @ResponseBody Object updateClientRequest(@PathVariable Long id, @RequestBody Client newData) {
        try {
            Client thisClient = clientRepo.findById(id).get();
            Long oldOIB = thisClient.getOib();
            if (newData.getFirstName() != null)
                thisClient.setFirstName(newData.getFirstName());
            if (newData.getLastName() != null)
                thisClient.setLastName(newData.getLastName());
            //if (newData.getStatus() != null)
            //    thisClient.setStatus(newData.getStatus());
            if (newData.getOib() != null)
                thisClient.setOib(newData.getOib());

            clientRepo.save(thisClient);

            if (!(Client.Status.REQUESTED == thisClient.getStatus())) {
                //Update if process started/inactive:
                updateProcess(thisClient, oldOIB);
            }

            return thisClient;
        } catch (Exception e) {
            // Patch will reject
            return "ERROR: Client request under ID: " + id + " does not exist.";
        }
    }

    @DeleteMapping(path = "client/card/{id}")
    public @ResponseBody String deleteClientRequest(@PathVariable Long id) {
        try {
            // Delete Client Request:
            Client thisClient = clientRepo.findById(id).get();
            thisClient.setStatus(Client.Status.INACTIVE);
            // Stop the process if started:
            String[] fileData = checkIfProcessStarted(thisClient.getOib());
            if (fileData != null) {
                if (fileData[0] != null) {
                    try {
                        if (Long.parseLong(fileData[0]) == id) {
                            //Set to inactive
                            updateProcess(thisClient, null);
                        }
                    } catch (Exception e) {
                        // Do nothing
                    }
                } // Else Do nothing
            }
            clientRepo.deleteById(id);
            return "Client request ID: " + thisClient.getId() + " is deleted."
                    + "\nData: " + thisClient.getFirstName() + " " + thisClient.getLastName()
                    + ", OIB: " + thisClient.getOib() + ", Status: " + thisClient.getStatus() + ".";
        } catch (Exception e) {
            return "ERROR: Client request under ID: " + id + " does not exist.";
        }
    }

    @DeleteMapping(path = "client/{oib}")
    public @ResponseBody String deleteClientRequests(@PathVariable Long oib) {
        if (oib == null || !checkOib(oib)) return "ERROR: Invalid OIB.";
        try {
            List<Client> allRequests = clientRepo.findByOib(oib);
            if (allRequests.isEmpty()) return "ERROR: No Client requests found for OIB: " + oib;
            String output = "";
            boolean flagFirst = true;
            for (Client thisClient : allRequests) {
                clientRepo.deleteById(thisClient.getId());
                if (flagFirst) {
                    flagFirst = false;
                    output = "Client request ID: " + thisClient.getId() + " is deleted."
                            + "\nData: " + thisClient.getFirstName() + " " + thisClient.getLastName()
                            + ", OIB: " + thisClient.getOib() + ", Status: " + thisClient.getStatus() + ".";
                } else {
                    output = output + "\n\nClient request ID: " + thisClient.getId() + " is deleted."
                            + "\nData: " + thisClient.getFirstName() + " " + thisClient.getLastName()
                            + ", OIB: " + thisClient.getOib() + ", Status: " + thisClient.getStatus() + ".";
                }
                thisClient.setStatus(Client.Status.INACTIVE);
                updateProcess(thisClient, null);
            }
            //Set process to inactive --> Delete
            stopProcess(oib);
            return output;
        } catch (Exception e) {
            return "ERROR: Client requests are not deleted. Internal Server Problem.";
        }
    }

    @PostMapping(path = "client/card/{id}/startProcess")
    public @ResponseBody Object startClientRequest(@PathVariable Long id) {
        try {
            Client thisClient = clientRepo.findById(id).get();
            if (thisClient.getStatus() == Client.Status.STARTED) {
                // This is same request:
                startProcess(thisClient);
                return "Process already started for Client request ID: " + thisClient.getId() + "."
                        + "\nData: " + thisClient.getFirstName() + " " + thisClient.getLastName()
                        + ", OIB: " + thisClient.getOib() + ", Status: " + thisClient.getStatus() + ".";
            }
            //File check
            String[] fileData = checkIfProcessStarted(thisClient.getOib());
            if (fileData == null) {
                //Start Process and Overwrite file
                thisClient.setStatus(Client.Status.STARTED);
                startProcess(thisClient);
            } else {
                if (fileData[0] != null) {
                    try {
                        if (Long.parseLong(fileData[0]) == id) {
                            // This is same request:
                            thisClient.setStatus(Client.Status.STARTED);
                            startProcess(thisClient);
                            //return at the end.
                        } else {
                            Client thisOtherClient = clientRepo.findById(Long.parseLong(fileData[0])).get();
                            if (Client.Status.INACTIVE == thisOtherClient.getStatus()) {
                                // Start next one instead:
                                thisClient.setStatus(Client.Status.STARTED);
                                startProcess(thisClient);
                                //return at the end.
                            } else {
                                return "Process already started for different Client request ID: " + thisOtherClient.getId() + "."
                                        + "\nData: " + thisOtherClient.getFirstName() + " " + thisOtherClient.getLastName()
                                        + ", OIB: " + thisOtherClient.getOib() + ", Status: " + thisOtherClient.getStatus() + ".";
                            }
                        }
                    } catch (Exception e) {
                        //Start Process and Overwrite file
                        System.out.println("ERROR: File Corrupted. Will be overwritten.");
                        thisClient.setStatus(Client.Status.STARTED);
                        startProcess(thisClient);
                    }
                } else {
                    //Start Process and Overwrite file
                    System.out.println("ERROR: File Corrupted. Will be overwritten.");
                    thisClient.setStatus(Client.Status.STARTED);
                    startProcess(thisClient);
                }
            }

            clientRepo.save(thisClient); //save new status

            return "Process started for Client request ID: " + thisClient.getId() + "."
                    + "\nData: " + thisClient.getFirstName() + " " + thisClient.getLastName()
                    + ", OIB: " + thisClient.getOib() + ", Status: " + thisClient.getStatus() + ".";
        } catch (Exception e) {
            return "ERROR: Client request under ID: " + id + " does not exist.";
        }
    }

    @DeleteMapping(path = "client/card/{id}/stopProcess")
    public @ResponseBody Object stopClientRequest(@PathVariable Long id) {
        try {
            Client thisClient = clientRepo.findById(id).get();
            thisClient.setStatus(Client.Status.INACTIVE);
            //File check
            String[] fileData = checkIfProcessStarted(thisClient.getOib());
            if (fileData == null) {
                return "Client request ID: " + thisClient.getId() + " not yet started.";
            } else {
                if (fileData[0] != null) {
                    try {
                        if (Long.parseLong(fileData[0]) == thisClient.getId()) {
                            //Set to inactive
                            updateProcess(thisClient, null);
                        } else {
                            // Some other ID is started
                            return "Client request ID: " + thisClient.getId() + " not yet started.";
                        }
                    } catch (Exception e) {
                        return "Client request ID: " + thisClient.getId() + " not yet started.";
                    }
                } else {
                    return "Client request ID: " + thisClient.getId() + " not yet started.";
                }
            }

            clientRepo.save(thisClient); //save new status

            return "Process stopped for Client request ID: " + thisClient.getId() + "."
                    + "\nData: " + thisClient.getFirstName() + " " + thisClient.getLastName()
                    + ", OIB: " + thisClient.getOib() + ", Status: " + thisClient.getStatus() + ".";
        } catch (Exception e) {
            return "ERROR: Client request under ID: " + id + " does not exist.";
        }
    }

    @DeleteMapping(path = "clients/killProcess")
    public @ResponseBody Object stopAllClientRequest() {
        try {
            //Remove the files:
            File[] directoryListing = dirOfStartedCardMakingProcesses.listFiles();
            File[] directoryListingDelete = directoryListing;
            if (directoryListing != null) {
                for (File child : directoryListing) {
                    child.delete();
                    System.out.println("File deleted: " + child.getName());
                }
            }
        } catch (Exception e) {
            // Do nothing
        }
        return "All card making processes are stopped";
    }

    private List<String> checkIfDataOk_Client(Client client) {
        List<String> missingParams = new ArrayList<>();
        if (client.getOib() == null) missingParams.add("oib");
        if (client.getFirstName() == null) missingParams.add("firstName");
        if (client.getLastName() == null) missingParams.add("lastName");
        //if (client.getStatus() == null) missingParams.add("status"); // Set by Db

        return missingParams;
    }

    private boolean checkOib(Long oib) {
        //OIB has to be sequence of 11 numbers
        // TODO: Missing full validation of OIB
        if (oib >= 10000000000l && oib < 100000000000l) {
            // This is "OK"
            return true;
        }
        return false;
    }

    // If id == null --> will not check for that specific request
    private String[] checkIfProcessStarted(Long oib) {
        String[] splitData = null;
        if (!dirOfStartedCardMakingProcesses.exists()) {
            dirOfStartedCardMakingProcesses.mkdir();
            return splitData;
        }
        File[] directoryListing = dirOfStartedCardMakingProcesses.listFiles();
        if (directoryListing != null) {
            for (File child : directoryListing) {
                //System.out.println(child.getName());
                if (child.getName().contains(oib + "")) {
                    try {
                        Scanner myReader = new Scanner(child);
                        while (myReader.hasNextLine()) {
                            String data = myReader.nextLine();
                            //System.out.println(data);
                            if (data != null && data.contains(fileDataDelimiter)) {
                                splitData = data.split(fileDataDelimiter);
                            }
                        }
                        myReader.close();
                        return splitData;
                    } catch (Exception e) {
                        System.out.println("ERROR: File " + child.getName() + " Corrupted.");
                    }
                }
                //System.out.println("File found: " + child.getName());
            }
        } else {
            System.out.println("Missing folder: " + pathOfFolder);
        }

        return splitData;
    }

    private void startProcess(Client thisClient) {
        if (!dirOfStartedCardMakingProcesses.exists()) {
            dirOfStartedCardMakingProcesses.mkdir();
        }

        //Cleanup first:
        stopProcess(thisClient.getOib());

        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String fileName = thisClient.getOib() + fileNameDelimiter + timeFormat.format(timestamp) + ".txt";

        String dataToSave = thisClient.getId() + fileDataDelimiter
                + thisClient.getOib() + fileDataDelimiter
                + thisClient.getFirstName() + fileDataDelimiter
                + thisClient.getLastName() + fileDataDelimiter
                + thisClient.getStatus();

        // Create a file:
        try {
            File myObj = new File(dirOfStartedCardMakingProcesses, fileName);
            if (myObj.createNewFile()) {
                FileWriter myWriter = new FileWriter(myObj);
                myWriter.write(dataToSave);
                myWriter.close();
                System.out.println("File created: " + myObj.getName());
            } else {
                // This should not happen:
                System.out.println("File already exists.");
            }
        } catch (Exception e) {
            System.out.println("ERROR: Cant create file: " + dirOfStartedCardMakingProcesses.getName() + "\\" + fileName);
        }
    }

    private void updateProcess(Client thisClient, Long oldOIB) {
        // You can pass oldOIB as null if not changed
        boolean flagOibCh = false;
        if (oldOIB != null && thisClient.getOib() != oldOIB) {
            // OIB changed
            flagOibCh = true;
        } else {
            // OIB is same
            oldOIB = thisClient.getOib();
        }
        try {
            //File check
            String[] fileData = checkIfProcessStarted(oldOIB);
            if (fileData != null) {
                if (fileData[0] != null) {
                    try {
                        if (Long.parseLong(fileData[0]) == thisClient.getId()) {
                            // Update Process:
                            if (flagOibCh) stopProcess(oldOIB); // Change of OIB
                            startProcess(thisClient);
                            System.out.println("WARNING: Process updated but is now under new name.");
                        }
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
            // Do nothing
        }
    }

    private void stopProcess(Long oib) {
        if (!dirOfStartedCardMakingProcesses.exists()) {
            dirOfStartedCardMakingProcesses.mkdir();
            return; // There is no file for sure
        }
        //Remove the file:
        File[] directoryListing = dirOfStartedCardMakingProcesses.listFiles();
        if (directoryListing != null) {
            for (File child : directoryListing) {
                if (child.getName().contains(oib + "")) {
                    child.delete();
                    System.out.println("File deleted: " + child.getName());
                    break;
                }
            }
        }
    }
}
