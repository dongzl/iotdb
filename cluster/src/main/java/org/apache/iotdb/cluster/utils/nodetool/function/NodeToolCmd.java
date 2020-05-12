/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.cluster.utils.nodetool.function;

import static java.lang.String.format;
import static org.apache.iotdb.cluster.utils.nodetool.Printer.errPrintln;

import com.google.common.base.Throwables;
import io.airlift.airline.Option;
import io.airlift.airline.OptionType;
import java.io.IOException;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.apache.iotdb.cluster.partition.PartitionGroup;
import org.apache.iotdb.cluster.rpc.thrift.Node;
import org.apache.iotdb.cluster.utils.nodetool.ClusterMonitor;
import org.apache.iotdb.cluster.utils.nodetool.ClusterMonitorMBean;

public abstract class NodeToolCmd implements Runnable {

  @Option(type = OptionType.GLOBAL, name = {"-h",
      "--host"}, description = "Node hostname or ip address")
  private String host = "127.0.0.1";

  @Option(type = OptionType.GLOBAL, name = {"-p",
      "--port"}, description = "Remote jmx agent port number")
  private String port = "31999";

  private static final String JMX_URL_FORMAT = "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi";

  public static final String BUILDING_CLUSTER_INFO = "The cluster is being created.";

  @Override
  public void run() {
    try {
      MBeanServerConnection mbsc = connect();
      ObjectName name = new ObjectName(ClusterMonitor.INSTANCE.getMBEAN_NAME());
      ClusterMonitorMBean clusterMonitorProxy = JMX
          .newMBeanProxy(mbsc, name, ClusterMonitorMBean.class);
      execute(clusterMonitorProxy);
    } catch (MalformedObjectNameException e) {
      errPrintln(e.getMessage());
    }
  }

  protected abstract void execute(ClusterMonitorMBean probe);

  private MBeanServerConnection connect() {
    MBeanServerConnection mbsc = null;

    try {
      String jmxURL = String.format(JMX_URL_FORMAT, host, port);
      JMXServiceURL serviceURL = new JMXServiceURL(jmxURL);
      JMXConnector connector = JMXConnectorFactory.connect(serviceURL);
      mbsc = connector.getMBeanServerConnection();
    } catch (IOException e) {
      Throwable rootCause = Throwables.getRootCause(e);
      errPrintln(format("nodetool: Failed to connect to '%s:%s' - %s: '%s'.", host, port,
          rootCause.getClass().getSimpleName(), rootCause.getMessage()));
      System.exit(1);
    }

    return mbsc;
  }

  String nodeToString(Node node){
    return String.format("%s:%d:%d", node.getIp(), node.getMetaPort(), node.getDataPort());
  }

  String partitionGroupToString(PartitionGroup group) {
    StringBuilder stringBuilder = new StringBuilder("[");
    if (!group.isEmpty()) {
      stringBuilder.append(nodeToString(group.get(0)));
    }
    for (int i = 1; i < group.size(); i++) {
      stringBuilder.append(", ").append(nodeToString(group.get(i)));
    }
    stringBuilder.append("]");
    return stringBuilder.toString();
  }
}