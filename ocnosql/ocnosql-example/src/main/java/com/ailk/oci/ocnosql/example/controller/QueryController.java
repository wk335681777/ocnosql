package com.ailk.oci.ocnosql.example.controller;

import com.ailk.oci.ocnosql.client.config.spi.*;
import com.ailk.oci.ocnosql.client.config.spi.Connection;
import com.ailk.oci.ocnosql.client.jdbc.*;
import com.ailk.oci.ocnosql.client.jdbc.phoenix.*;
import com.ailk.oci.ocnosql.client.query.criterion.*;
import com.ailk.oci.ocnosql.client.spi.*;
import com.ailk.oci.ocnosql.common.util.*;
import com.ailk.oci.ocnosql.example.model.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.lang.*;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.mvc.multiaction.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.text.*;
import java.text.ParseException;
import java.util.*;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: lile3
 * Date: 14-4-17
 * Time: 下午3:07
 * To change this template use File | Settings | File Templates.
 */
@Controller
@RequestMapping("/common")
public class QueryController extends MultiActionController{

    public static final String TABLENAME = "DR_QUERY_TEST";



    @RequestMapping(method = RequestMethod.POST)
	public ModelAndView postQueryDetailRecord(HttpServletRequest req,
			HttpServletResponse resp) {

		return queryDetailRecord(req, resp);
	}


	@RequestMapping(method = RequestMethod.GET)
	public ModelAndView queryDetailRecord(HttpServletRequest req,
			HttpServletResponse resp) {

        String reqType = req.getParameter("type");
        if(reqType.equals("queryByHBaseAPI")){
          queryByHBaseAPI(req,resp);
        }else if(reqType.equals("queryBySQL")){
          queryBySqlAPI(req,resp);
        }else if(reqType.equals("put")){
          putByHBaseAPI(req,resp);
        }
        return null;
	}


    //HBase API for query
    public void queryByHBaseAPI(HttpServletRequest req,HttpServletResponse resp){
        ClientAdaptor service = new ClientAdaptor();
		String mobile = req.getParameter("mobile");
        String startTime = req.getParameter("startTime");
        String endTime = req.getParameter("endTime");

        String startRowkey = mobile+startTime;
        String endRowkey = mobile + endTime;
        String tablePrefix = TABLENAME;
		List<Map<String,String>> result = null;
        try {

            List<String> tables = new ArrayList<String>();
            tables.add(tablePrefix+startTime.substring(0,6));

            Criterion criterion =  buildCriterion(req);

            Connection conn = Connection.getInstance();

            List<String[]> originalRes = service.queryByRowkey(conn,startRowkey,endRowkey,tables,criterion,null,null);

            BusiModelReader.loadModel("classpath:drquery.model/busiMapping/*.xml");
            MetaModel busiMeta = BusiModelReader.getMetaModels().get("GPRS"); //与配置文件相对应
            result = processData(originalRes, req, busiMeta);
            String jsonStr = result.toString();

            resp.getWriter().write(jsonStr);
            resp.flushBuffer();
		} catch (Exception e) {
            e.printStackTrace();
		}
    }

    //sql for query
    public List<Map<String,String>> queryBySqlAPI(HttpServletRequest req,HttpServletResponse resp){
        String mobile = req.getParameter("mobile");
        String startTime = req.getParameter("startTime");
        String endTime = req.getParameter("endTime");
        String appName = req.getParameter("APPNAME");
        String sql = "select * from "+TABLENAME+startTime.substring(0,6)+
                    " where id>='"+ mobile+startTime + "' and id<='" + mobile+endTime + "'";
        if(!StringUtils.isEmpty(appName)){
           sql = sql + " and APPNAME='weibo'";
        }
        System.out.println("sql = " + sql);
        List<Map<String,String>> result = null;
        try {
            result = loadDR(sql, null);
            String jsonStr = result.toString();
            resp.getWriter().write(jsonStr);
            resp.flushBuffer();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e){
            e.printStackTrace();
        }
        return result;
    }

    //HBase API for put
    public void putByHBaseAPI(HttpServletRequest req,HttpServletResponse resp){
       ClientAdaptor service = new ClientAdaptor();
       String tableName = req.getParameter(CommonConstants.TABLE_NAME);
       String records = req.getParameter("records");
       String[] recordArr = StringUtils.splitByWholeSeparatorPreserveAllTokens(records,"|");
       Map map = new HashMap();
       Map paramMap = req.getParameterMap();
       Iterator<String> it = paramMap.keySet().iterator();
       while (it.hasNext()){
          String key = it.next();
          Object value = paramMap.get(key);
          map.put(key,((String[])value)[0]);
       }
       service.putData(tableName,recordArr,map);
    }


    public List<Map<String, String>> loadDR(String sql,Object[] args)throws SQLException{
		List<Map<String, String>>  retArr = new ArrayList<Map<String, String>>();
        HbaseJdbcHelper jdbcHelper = null;
        try{
			long t1 = System.currentTimeMillis();
			jdbcHelper = new PhoenixJdbcHelper();
			ResultSet rsResultSet = jdbcHelper.executeQueryRaw(sql,args);
            int columnCount = rsResultSet.getMetaData().getColumnCount();
            while (rsResultSet.next()) {
                Map<String, String> map = new HashMap<String, String>();
                for (int i = 1; i <= columnCount; i++) {
                    String metaData = rsResultSet.getMetaData().getColumnName(i);
                    Object valueData = rsResultSet.getObject(i);
                    String value=valueData + "";
                    map.put(metaData,value);
                }
                retArr.add(map);
            }
		} catch(Exception ex){
				throw new SQLException("ocnosql found exception",ex);
		}finally{
           try{
               if(jdbcHelper != null){
                  jdbcHelper.close();
               }
           }catch (SQLException e){
              throw new SQLException("close connection error",e);
           }
        }
		return retArr;
	}

	private Criterion buildCriterion(HttpServletRequest req){
           String appName = req.getParameter("APPNAME");
           if(!StringUtils.isEmpty(appName)){
               Criterion criterion = new Criterion();
               criterion.setEqualsTo("F","APPNAME",appName);
               return criterion;
           }
            return null;
	}

    public List<Map<String, String>> processData(List<String[]> retArr, HttpServletRequest req,MetaModel busiMeta){
		List<Map<String, String>> retData = new ArrayList<Map<String, String>>();
        for(int i=0; i < retArr.size(); i++){
            String[] rowData = 	retArr.get(i);
            Map<String, String> record = convertRowDataToUniformStructure(rowData, busiMeta.getModelId());
            retData.add(record);
        }
		return retData;
	}

    public Map<String, String> convertRowDataToUniformStructure(String[] rowData, String modelId){
    	Map<String, Field> fields = BusiModelReader.getMetaModels().get(modelId).getFields();
    	Map<String, String> map = new HashMap();

    	for(Iterator it = fields.keySet().iterator(); it.hasNext();){
    		String key = (String)it.next();
    		String value  =  rowData[fields.get(key).getIndex() - 1];
    		map.put(key,value);
    	}
    	return map;
    }
}
