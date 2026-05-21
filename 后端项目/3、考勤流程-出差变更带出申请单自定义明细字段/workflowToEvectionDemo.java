package ecologyDemo.blog;

import com.engine.autoLink.common.util.Util;
import com.engine.autoLink.workflow.util.WorkflowUtil;
import com.engine.kq.biz.KQFlowActiontBiz;
import com.engine.kq.enums.KqSplitFlowTypeEnum;
import com.engine.kq.wfset.util.KQFlowEvectionUtil;
import com.engine.kq.wfset.util.KQFlowUtil;
import weaver.conn.RecordSet;
import weaver.hrm.resource.ResourceComInfo;
import weaver.interfaces.workflow.action.Action;
import weaver.soa.workflow.request.RequestInfo;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @DESCRIPTION:
 * @USER: solelyr
 * @DATE: 2026/5/21 下午1:54
 */
public class workflowToEvectionDemo implements Action {
    @Override
    public String execute(RequestInfo requestInfo) {
        try {
            Map<String,Object> mainData = WorkflowUtil.getMainData(requestInfo); // 如无该方法，可通过sql获取主表数据
            String changeRequestId = Util.null2String(mainData.get("changerequestid")); // 变更流程
            String changeType = Util.null2String(mainData.get("changetype")); // 变更类型
            if("1".equals(changeType)){
                // 撤销
                changeStatusDeactivate(changeRequestId,true); // 接口执行成功后再操作将历史数据作废
                return Action.SUCCESS;
            }

            KQFlowEvectionUtil kqFlowEvectionUtil = new KQFlowEvectionUtil();
            Map sqlMap = new HashMap(); // this.handleSql(var1, var2, var3, var4, var8);
            String tableName = requestInfo.getRequestManager().getBillTableName();
            int workflowId = Util.getIntValue(requestInfo.getWorkflowid());
            int requestId = Util.getIntValue(requestInfo.getRequestid(),-1);

            sqlMap.put(tableName+"###"+tableName+"_dt2###1###"+workflowId,"select t.*,b.requestname,b.status,c.lastname,c.departmentId c_departmentId,d.departmentname,c.subcompanyid1,c.workcode from (select t.requestid, t1.id as detailId,t.bm as detail_departmentId, t1.sc as detail_duration, t1.ksrq as detail_fromDate, t1.kssj as detail_fromTime, t.resourceId as detail_resourceId, t1.jsrq as detail_toDate, t1.jssj as detail_toTime from " + tableName + " t left join " + tableName + "_dt2 t1 on t.id = t1.mainid) t left join Workflow_Requestbase b on t.requestid = b.requestid left join HrmResource c on  t.detail_resourceId = c.id  left join HrmDepartment d on c.departmentId = d.id where 1 = 1 and workflowid = "+ workflowId +" and t.requestId = " + requestId);
            ArrayList dataList = new ArrayList();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            Map resultMap = kqFlowEvectionUtil.handleKQEvectionAction(sqlMap, dataList, formatter, workflowId, requestId, resourceComInfo);
            KqSplitFlowTypeEnum typeEnum = KqSplitFlowTypeEnum.EVECTION;
            KQFlowUtil kqFlowUtil = new KQFlowUtil();
            kqFlowUtil.handleSplitFLowActionData(dataList, typeEnum, resourceComInfo,resultMap, false, requestId, workflowId, false);

            KQFlowActiontBiz kqFlowActiontBiz = new KQFlowActiontBiz();
            kqFlowActiontBiz.handle_flow_deduct_card(workflowId, dataList, requestId);
            if (!resultMap.isEmpty()) {
                requestInfo.getRequestManager().setMessagecontent(Util.null2String((resultMap.get("message"))));
                return Action.FAILURE_AND_CONTINUE;
            }
            changeStatusDeactivate(changeRequestId,true); // 接口执行成功后再操作将历史数据作废
            deleteOut(Util.null2String(requestId));
        }catch (Exception e) {
            e.printStackTrace();
            return Action.FAILURE_AND_CONTINUE;
        }
        return Action.SUCCESS;
    }


    /**
     * 修改为停用状态
     * @param requestId
     * @param status true表示停用 false表示启用
     * @return
     */
    private boolean changeStatusDeactivate(String requestId, Boolean status){
        String sql = "update kq_flow_split_evection set status = ? where requestid = ?";
        RecordSet rs = new RecordSet();
        return rs.executeUpdate(sql, status ? 1 : 0, requestId);
    }

    /**
     * 删除相同流程生成的外出明细，避免某些SB先提交一个外出的变更，再撤回提交一个出差的变更
     * @param requestId
     * @return
     */
    private boolean deleteOut(String requestId){
        String sql = "delete from kq_flow_split_out WHERE requestid = ?";
        RecordSet rs = new RecordSet();
        return rs.executeUpdate(sql, requestId);
    }

}
