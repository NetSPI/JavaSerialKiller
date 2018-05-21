package burp;

import com.google.common.primitives.Bytes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

public class ChildTab implements IMessageEditorController, ActionListener {

    private final IMessageEditor requestViewer;
    private final IMessageEditor responseViewer;
    private final IBurpExtenderCallbacks callbacks;
    private final IExtensionHelpers helpers;
    private final IHttpService httpService;

    private byte[] request;
    private byte[] response;

    public static byte[] selectedMessage;

    private final JPanel panel;

    public static boolean isEncoded;
    public static boolean isGzCompressed;

    JButton goButton;
    JCheckBox base64CheckBox;
    JCheckBox compressGzCheckBox;

    private final JComboBox<String> payloadComboBox;

    private final JTextField commandTextField;

    private final JLabel status;

     public ChildTab(final IBurpExtenderCallbacks callbacks, JTabbedPane tabbedPane, String title, byte[] request, IHttpService httpService) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        this.httpService = httpService;
        this.request = request;

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
        requestViewer = callbacks.createMessageEditor(this, true);
        responseViewer = callbacks.createMessageEditor(this, false);
        requestViewer.setMessage(request, true);

        JPanel leftSplitPanePanel = new JPanel();
        leftSplitPanePanel.setLayout(new BorderLayout());

        leftSplitPanePanel.add(requestViewer.getComponent());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setAlignmentX(Component.CENTER_ALIGNMENT);
        splitPane.setResizeWeight(0.5);
        splitPane.setLeftComponent(leftSplitPanePanel);
        splitPane.setRightComponent(responseViewer.getComponent());

        JPanel topButtonPanel = new JPanel();
        topButtonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        goButton = new JButton("Go");
        goButton.setActionCommand("go");
        goButton.addActionListener(ChildTab.this);
        JButton serializeButton = new JButton("Serialize");
        serializeButton.setActionCommand("serialize");
        serializeButton.addActionListener(ChildTab.this);

        base64CheckBox = new JCheckBox("Base64 Encode");
        compressGzCheckBox = new JCheckBox("Compress (GZip)");

        String[] typeStrings = { "BeanShell1","CommonsBeanutilsCollectionsLogging1", "CommonsCollections1", "CommonsCollections2", "CommonsCollections3", "CommonsCollections4","Groovy1","Jdk7u21","Spring1"};
        payloadComboBox = new JComboBox<>(typeStrings);
        JButton helpButton = new JButton("?");
        helpButton.setActionCommand("?");
        helpButton.addActionListener(ChildTab.this);
        topButtonPanel.add(goButton);
        topButtonPanel.add(serializeButton);
        topButtonPanel.add(base64CheckBox);
        topButtonPanel.add(compressGzCheckBox);
        topButtonPanel.add(payloadComboBox);
        topButtonPanel.add(helpButton);

        JPanel commandPanel = new JPanel();
        commandPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel commandLabel = new JLabel("Command: ");
        commandTextField = new JTextField(50);
        commandTextField.setMaximumSize(new Dimension(Integer.MAX_VALUE, commandTextField.getPreferredSize().height));
        commandPanel.add(commandLabel);
        commandPanel.add(commandTextField);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        status = new JLabel("Done");
        bottomPanel.add(status);

        panel.add(topButtonPanel);
        panel.add(commandPanel);
        panel.add(splitPane);
        panel.add(bottomPanel);

        tabbedPane.add(title, panel);
        tabbedPane.setTabComponentAt(JavaSerialKillerTab.tabCount - JavaSerialKillerTab.removedTabCount, new ButtonTabComponent(tabbedPane));

    }

    private void sendRequest() {

        Thread thread = new Thread() {
            public void run() {
                IRequestInfo iRequestInfo = helpers.analyzeRequest(requestViewer.getMessage());
                byte[] requestMessage = requestViewer.getMessage();
                java.util.List<String> headers = iRequestInfo.getHeaders();
                int bodyOffset = iRequestInfo.getBodyOffset();
                byte[] newBody = new byte[requestMessage.length - bodyOffset];

                System.arraycopy(requestMessage, bodyOffset, newBody, 0, requestMessage.length - bodyOffset);

                byte[] requestHTTPMessage = helpers.buildHttpMessage(headers, newBody);
                responseViewer.setMessage(new byte[0], false);
                status.setText("Waiting");
                goButton.setEnabled(false);
                IHttpRequestResponse httpMessage = callbacks.makeHttpRequest(httpService, requestHTTPMessage);
                status.setText("Done");
                responseViewer.setMessage(httpMessage.getResponse(), false);
                response = httpMessage.getResponse();
                goButton.setEnabled(true);
            }
        };
        thread.start();

    }

    private void serializeRequest() {

        byte[] message = requestViewer.getMessage();

        byte[] selectedMessage = requestViewer.getSelectedData();

        // String[] command = Utilities.formatCommand(commandTextField.getText());

        boolean isEncoded = base64CheckBox.isSelected();
        boolean isGzCompressed = compressGzCheckBox.isSelected();

        String command = commandTextField.getText();

        String payloadType = payloadComboBox.getSelectedItem().toString();

        byte[] httpMessage = Utilities.serializeRequest(message,selectedMessage,isEncoded,isGzCompressed,command,helpers,payloadType);

        requestViewer.setMessage(httpMessage, true);

        request = httpMessage;
    }

    private void questionDialog(){

        JOptionPane.showMessageDialog(this.panel,"BeanShell1 [org.beanshell:bsh:2.0b5]\n" +
                "CommonsBeanutilsCollectionsLogging1 [commons-beanutils:commons-beanutils:1.9.2, commons-collections:commons-collections:3.1, commons-logging:commons-logging:1.2]\n" +
                        "CommonsCollections1 [commons-collections:commons-collections:3.1]\n" +
                        "CommonsCollections2 [org.apache.commons:commons-collections4:4.0]\n" +
                        "CommonsCollections3 [commons-collections:commons-collections:3.1]\n" +
                        "CommonsCollections4 [org.apache.commons:commons-collections4:4.0]\n" +
                        "Groovy1 [org.codehaus.groovy:groovy:2.3.9]\n" +
                        "Jdk7u21 []\n" +
                        "Spring1 [org.springframework:spring-core:4.1.4.RELEASE, org.springframework:spring-beans:4.1.4.RELEASE]",
                "ysoserial Payload Options",
                JOptionPane.PLAIN_MESSAGE);

    }

    public void actionPerformed(ActionEvent event) {

        String command = event.getActionCommand();

        switch (command) {
            case "go":
                sendRequest();
                break;
            case "serialize":
                serializeRequest();
                break;
            case "?":
                questionDialog();
                break;
        }
    }

    public IHttpService getHttpService() {
       return httpService;
    }

    public byte[] getRequest() {
        return request;
    }

    public byte[] getResponse() {
        return response;
    }
}