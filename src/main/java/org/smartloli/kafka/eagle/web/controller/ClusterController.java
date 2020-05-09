/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartloli.kafka.eagle.web.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.smartloli.kafka.eagle.common.util.JMXFactoryUtils;
import org.smartloli.kafka.eagle.common.util.KConstants;
import org.smartloli.kafka.eagle.common.util.KConstants.BrokerSever;
import org.smartloli.kafka.eagle.common.util.KConstants.Kafka;
import org.smartloli.kafka.eagle.common.util.StrUtils;
import org.smartloli.kafka.eagle.common.util.SystemConfigUtils;
import org.smartloli.kafka.eagle.web.service.ClusterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.TimeUnit;

/**
 * Kafka & Zookeeper controller to viewer data.
 * 
 * @author smartloli.
 *
 *         Created by Sep 6, 2016.
 * 
 *         Update by hexiang 20170216
 */
@Controller
public class ClusterController {

	@Autowired
	private ClusterService clusterService;

	/** Cluster viewer. */
	@RequestMapping(value = "/cluster/info", method = RequestMethod.GET)
	public ModelAndView clusterView(HttpSession session) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("/cluster/cluster");
		return mav;
	}

	/** Cluster viewer. */
	@RequestMapping(value = "/cluster/multi", method = RequestMethod.GET)
	public ModelAndView clustersView(HttpSession session) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("/cluster/multicluster");
		return mav;
	}

	/** Zookeeper client viewer. */
	@RequiresPermissions("/cluster/zkcli")
	@RequestMapping(value = "/cluster/zkcli", method = RequestMethod.GET)
	public ModelAndView zkCliView(HttpSession session) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("/cluster/zkcli");
		return mav;
	}

	/** Get cluster data by ajax. */
	@RequestMapping(value = "/cluster/info/{type}/ajax", method = RequestMethod.GET)
	public void clusterAjax(@PathVariable("type") String type, HttpServletResponse response, HttpServletRequest request) {
		String aoData = request.getParameter("aoData");
		JSONArray params = JSON.parseArray(aoData);
		int sEcho = 0, iDisplayStart = 0, iDisplayLength = 0;
		String search = "";
		for (Object object : params) {
			JSONObject param = (JSONObject) object;
			if ("sEcho".equals(param.getString("name"))) {
				sEcho = param.getIntValue("value");
			} else if ("iDisplayStart".equals(param.getString("name"))) {
				iDisplayStart = param.getIntValue("value");
			} else if ("iDisplayLength".equals(param.getString("name"))) {
				iDisplayLength = param.getIntValue("value");
			} else if ("sSearch".equals(param.getString("name"))) {
				search = param.getString("value");
			}
		}

		HttpSession session = request.getSession();
		String clusterAlias = session.getAttribute(KConstants.SessionAlias.CLUSTER_ALIAS).toString();

		JSONObject deserializeClusters = JSON.parseObject(clusterService.get(clusterAlias, type));
		JSONArray clusters = deserializeClusters.getJSONArray(type);
		int offset = 0;
		JSONArray aaDatas = new JSONArray();

		JMXConnector connector = null;
		String JMX = "service:jmx:rmi:///jndi/rmi://%s/jmxrmi";
		String memory = "<a class='btn btn-danger btn-xs'>NULL</a>";
		for (Object object : clusters) {
			JSONObject cluster = (JSONObject) object;
			if (search.length() > 0 && search.equals(cluster.getString("host"))) {
				JSONObject obj = new JSONObject();
				obj.put("id", cluster.getInteger("id"));
				obj.put("port", cluster.getInteger("port"));
				obj.put("ip", cluster.getString("host"));
				if ("kafka".equals(type)) {
					obj.put("jmxPort", cluster.getInteger("jmxPort"));
					try {
						JMXServiceURL jmxSeriverUrl = new JMXServiceURL(String.format(JMX, cluster.getString("host") + ":" + cluster.getInteger("jmxPort")));
						connector = JMXFactoryUtils.connectWithTimeout(jmxSeriverUrl, 30, TimeUnit.SECONDS);
						MBeanServerConnection mbeanConnection = connector.getMBeanServerConnection();
						MemoryMXBean memBean = ManagementFactory.newPlatformMXBeanProxy(mbeanConnection, ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);
						long used = memBean.getHeapMemoryUsage().getUsed();
						long max = memBean.getHeapMemoryUsage().getMax();
						String percent = StrUtils.stringify(used) + " (" + StrUtils.numberic((used * 100.0 / max) + "") + "%)";
						if ((used * 100.0) / max < BrokerSever.MEM_NORMAL) {
							memory = "<a class='btn btn-success btn-xs'>" + percent + "</a>";
						} else if ((used * 100.0) / max >= BrokerSever.MEM_NORMAL && (used * 100.0) / max < BrokerSever.MEM_DANGER) {
							memory = "<a class='btn btn-warning btn-xs'>" + percent + "</a>";
						} else if ((used * 100.0) / max >= BrokerSever.MEM_DANGER) {
							memory = "<a class='btn btn-danger btn-xs'>" + percent + "</a>";
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					obj.put("memory", memory);
					obj.put("created", cluster.getString("created"));
					obj.put("modify", cluster.getString("modify"));
					String version = clusterService.getKafkaVersion(cluster.getString("host"), cluster.getInteger("jmxPort"), cluster.getString("ids"), clusterAlias);
					version = (version == "" ? Kafka.UNKOWN : version);
					if (Kafka.UNKOWN.equals(version)) {
						obj.put("version", "<a class='btn btn-danger btn-xs'>" + version + "</a>");
					} else {
						obj.put("version", "<a class='btn btn-success btn-xs'>" + version + "</a>");
					}
				} else if ("zk".equals(type)) {
					String mode = cluster.getString("mode");
					if ("death".equals(mode)) {
						obj.put("mode", "<a class='btn btn-danger btn-xs'>" + mode + "</a>");
					} else {
						obj.put("mode", "<a class='btn btn-success btn-xs'>" + mode + "</a>");
					}
					String version = cluster.getString("version");
					if (StrUtils.isNull(version)) {
						obj.put("version", "<a class='btn btn-danger btn-xs'>unkown</a>");
					} else {
						obj.put("version", "<a class='btn btn-success btn-xs'>" + version + "</a>");
					}
				}
				aaDatas.add(obj);
			} else if (search.length() == 0) {
				if (offset < (iDisplayLength + iDisplayStart) && offset >= iDisplayStart) {
					JSONObject obj = new JSONObject();
					obj.put("id", cluster.getInteger("id"));
					obj.put("port", cluster.getInteger("port"));
					obj.put("ip", cluster.getString("host"));
					if ("kafka".equals(type)) {
						obj.put("jmxPort", cluster.getInteger("jmxPort"));
						try {
							JMXServiceURL jmxSeriverUrl = new JMXServiceURL(String.format(JMX, cluster.getString("host") + ":" + cluster.getInteger("jmxPort")));
							connector = JMXFactoryUtils.connectWithTimeout(jmxSeriverUrl, 30, TimeUnit.SECONDS);
							MBeanServerConnection mbeanConnection = connector.getMBeanServerConnection();
							MemoryMXBean memBean = ManagementFactory.newPlatformMXBeanProxy(mbeanConnection, ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);
							long used = memBean.getHeapMemoryUsage().getUsed();
							long max = memBean.getHeapMemoryUsage().getMax();
							String percent = StrUtils.stringify(used) + " (" + StrUtils.numberic((used * 100.0 / max) + "") + "%)";
							if ((used * 100.0) / max < BrokerSever.MEM_NORMAL) {
								memory = "<a class='btn btn-success btn-xs'>" + percent + "</a>";
							} else if ((used * 100.0) / max >= BrokerSever.MEM_NORMAL && (used * 100.0) / max < BrokerSever.MEM_DANGER) {
								memory = "<a class='btn btn-warning btn-xs'>" + percent + "</a>";
							} else if ((used * 100.0) / max >= BrokerSever.MEM_DANGER) {
								memory = "<a class='btn btn-danger btn-xs'>" + percent + "</a>";
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
						obj.put("memory", memory);
						obj.put("created", cluster.getString("created"));
						obj.put("modify", cluster.getString("modify"));
						String version = clusterService.getKafkaVersion(cluster.getString("host"), cluster.getInteger("jmxPort"), cluster.getString("ids"), clusterAlias);
						version = (version == "" ? Kafka.UNKOWN : version);
						if (Kafka.UNKOWN.equals(version)) {
							obj.put("version", "<a class='btn btn-danger btn-xs'>" + version + "</a>");
						} else {
							obj.put("version", "<a class='btn btn-success btn-xs'>" + version + "</a>");
						}
					} else if ("zk".equals(type)) {
						String mode = cluster.getString("mode");
						if ("death".equals(mode)) {
							obj.put("mode", "<a class='btn btn-danger btn-xs'>" + mode + "</a>");
						} else {
							obj.put("mode", "<a class='btn btn-success btn-xs'>" + mode + "</a>");
						}
						String version = cluster.getString("version");
						if (StrUtils.isNull(version)) {
							obj.put("version", "<a class='btn btn-danger btn-xs'>unkown</a>");
						} else {
							obj.put("version", "<a class='btn btn-success btn-xs'>" + version + "</a>");
						}
					}
					aaDatas.add(obj);
				}
				offset++;
			}
		}

		JSONObject target = new JSONObject();
		target.put("sEcho", sEcho);
		target.put("iTotalRecords", clusters.size());
		target.put("iTotalDisplayRecords", clusters.size());
		target.put("aaData", aaDatas);
		try {
			byte[] output = target.toJSONString().getBytes();
			BaseController.response(output, response);
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (connector != null) {
				try {
					connector.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/** Change cluster viewer address. */
	@RequestMapping(value = "/cluster/info/{clusterAlias}/change", method = RequestMethod.GET)
	public ModelAndView clusterChangeAjax(@PathVariable("clusterAlias") String clusterAlias, HttpSession session, HttpServletResponse response, HttpServletRequest request) {
		if (!clusterService.hasClusterAlias(clusterAlias)) {
			return new ModelAndView("redirect:/error/404");
		} else {
			session.removeAttribute(KConstants.SessionAlias.CLUSTER_ALIAS);
			session.setAttribute(KConstants.SessionAlias.CLUSTER_ALIAS, clusterAlias);
			String[] clusterAliass = SystemConfigUtils.getPropertyArray("kafka.eagle.zk.cluster.alias", ",");
			String dropList = "<ul class='dropdown-menu'>";
			int i = 0;
			for (String clusterAliasStr : clusterAliass) {
				if (!clusterAliasStr.equals(clusterAlias) && i < KConstants.SessionAlias.CLUSTER_ALIAS_LIST_LIMIT) {
					dropList += "<li><a href='/ke/cluster/info/" + clusterAliasStr + "/change'><i class='fa fa-fw fa-sitemap'></i>" + clusterAliasStr + "</a></li>";
					i++;
				}
			}
			dropList += "<li><a href='/ke/cluster/multi'><i class='fa fa-fw fa-tasks'></i>More...</a></li></ul>";
			session.removeAttribute(KConstants.SessionAlias.CLUSTER_ALIAS_LIST);
			session.setAttribute(KConstants.SessionAlias.CLUSTER_ALIAS_LIST, dropList);
			return new ModelAndView("redirect:/");
		}
	}

	/** Get multicluster information. */
	@RequestMapping(value = "/cluster/info/multicluster/ajax", method = RequestMethod.GET)
	public void multiClusterAjax(HttpServletResponse response, HttpServletRequest request) {
		String aoData = request.getParameter("aoData");
		JSONArray params = JSON.parseArray(aoData);
		int sEcho = 0, iDisplayStart = 0, iDisplayLength = 0;
		String search = "";
		for (Object object : params) {
			JSONObject param = (JSONObject) object;
			if ("sEcho".equals(param.getString("name"))) {
				sEcho = param.getIntValue("value");
			} else if ("iDisplayStart".equals(param.getString("name"))) {
				iDisplayStart = param.getIntValue("value");
			} else if ("iDisplayLength".equals(param.getString("name"))) {
				iDisplayLength = param.getIntValue("value");
			} else if ("sSearch".equals(param.getString("name"))) {
				search = param.getString("value");
			}
		}

		JSONArray clusterAliass = clusterService.clusterAliass();
		int offset = 0;
		JSONArray aaDatas = new JSONArray();
		for (Object object : clusterAliass) {
			JSONObject cluster = (JSONObject) object;
			if (search.length() > 0 && cluster.getString("clusterAlias").contains(search)) {
				JSONObject target = new JSONObject();
				target.put("id", cluster.getInteger("id"));
				target.put("clusterAlias", cluster.getString("clusterAlias"));
				target.put("zkhost", cluster.getString("zkhost"));
				target.put("operate", "<a name='change' href='#" + cluster.getString("clusterAlias") + "' class='btn btn-primary btn-xs'>Change</a>");
				aaDatas.add(target);
			} else if (search.length() == 0) {
				if (offset < (iDisplayLength + iDisplayStart) && offset >= iDisplayStart) {
					JSONObject target = new JSONObject();
					target.put("id", cluster.getInteger("id"));
					target.put("clusterAlias", cluster.getString("clusterAlias"));
					target.put("zkhost", cluster.getString("zkhost"));
					target.put("operate", "<a name='change' href='#" + cluster.getString("clusterAlias") + "' class='btn btn-primary btn-xs'>Change</a>");
					aaDatas.add(target);
				}
				offset++;
			}
		}

		JSONObject target = new JSONObject();
		target.put("sEcho", sEcho);
		target.put("iTotalRecords", clusterAliass.size());
		target.put("iTotalDisplayRecords", clusterAliass.size());
		target.put("aaData", aaDatas);
		try {
			byte[] output = target.toJSONString().getBytes();
			BaseController.response(output, response);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/** Get zookeeper client whether live data by ajax. */
	@RequestMapping(value = "/cluster/zk/islive/ajax", method = RequestMethod.GET)
	public void zkCliLiveAjax(HttpServletResponse response, HttpServletRequest request) {
		HttpSession session = request.getSession();
		String clusterAlias = session.getAttribute(KConstants.SessionAlias.CLUSTER_ALIAS).toString();

		try {
			byte[] output = clusterService.status(clusterAlias).toJSONString().getBytes();
			BaseController.response(output, response);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/** Execute zookeeper command by ajax. */
	@RequestMapping(value = "/cluster/zk/cmd/ajax", method = RequestMethod.GET)
	public void zkCliCmdAjax(HttpServletResponse response, HttpServletRequest request) {
		String cmd = request.getParameter("cmd");
		String type = request.getParameter("type");

		HttpSession session = request.getSession();
		String clusterAlias = session.getAttribute(KConstants.SessionAlias.CLUSTER_ALIAS).toString();

		try {
			byte[] output = clusterService.execute(clusterAlias, cmd, type).getBytes();
			BaseController.response(output, response);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

}
