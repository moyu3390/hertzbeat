package com.usthe.manager.service.impl;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.usthe.common.entity.alerter.Alert;
import com.usthe.common.util.CommonConstants;
import com.usthe.manager.component.alerter.DispatcherAlarm;
import com.usthe.manager.dao.NoticeReceiverDao;
import com.usthe.manager.dao.NoticeRuleDao;
import com.usthe.common.entity.manager.NoticeReceiver;
import com.usthe.common.entity.manager.NoticeRule;
import com.usthe.manager.service.NoticeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消息通知配置实现
 *
 * @author tom
 * @date 2021/12/16 16:16
 */
@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class NoticeConfigServiceImpl implements NoticeConfigService {

    private static final String ALERT_TEST_TARGET = "Test Target";

    private static final String ALERT_TEST_CONTENT = "test send msg! \n This is the test data. It is proved that it can be received successfully";

    @Autowired
    private NoticeReceiverDao noticeReceiverDao;

    @Autowired
    private NoticeRuleDao noticeRuleDao;

    @Autowired
    @Lazy
    private DispatcherAlarm dispatcherAlarm;

    @Override
    public List<NoticeReceiver> getNoticeReceivers(Specification<NoticeReceiver> specification) {
        return noticeReceiverDao.findAll(specification);
    }

    @Override
    public List<NoticeRule> getNoticeRules(Specification<NoticeRule> specification) {
        return noticeRuleDao.findAll(specification);
    }

    @Override
    public void addReceiver(NoticeReceiver noticeReceiver) {
        noticeReceiverDao.save(noticeReceiver);
    }

    @Override
    public void editReceiver(NoticeReceiver noticeReceiver) {
        noticeReceiverDao.save(noticeReceiver);
    }

    @Override
    public void deleteReceiver(Long receiverId) {
        noticeReceiverDao.deleteById(receiverId);
    }

    @Override
    public void addNoticeRule(NoticeRule noticeRule) {
        noticeRuleDao.save(noticeRule);
    }

    @Override
    public void editNoticeRule(NoticeRule noticeRule) {
        noticeRuleDao.save(noticeRule);
    }

    @Override
    public void deleteNoticeRule(Long ruleId) {
        noticeRuleDao.deleteById(ruleId);
    }

    @Override
    public List<NoticeReceiver> getReceiverFilterRule(Alert alert) {
        // todo use cache   使用缓存
        List<NoticeRule> rules = noticeRuleDao.findNoticeRulesByEnableTrue();

        // todo The temporary rule is to forward all, and then implement more matching rules: alarm status selection, monitoring type selection, etc.
        // 暂时规则是全部转发 后面实现更多匹配规则：告警状态选择 监控类型选择等
        Set<Long> receiverIds = rules.stream()
                .filter(NoticeRule::isFilterAll)
                .map(NoticeRule::getReceiverId)
                .collect(Collectors.toSet());
        // 除了全部转发的 其他的按照tags标签和告警级别过滤匹配
        Set<Long> receiverIdsByMatch = rules.stream()
                .filter(rule -> !rule.isFilterAll())
                .filter(rule -> {
                    MapDifference<String, Object> difference = Maps.difference(alert.getTags(), rule.getTags() == null ? Maps.newHashMap() : rule.getTags());
                    Map<String, Object> difMap= difference.entriesInCommon();
                    if (rule.getPriorities() == null || rule.getPriorities().isEmpty()) {
                        return !difMap.isEmpty();
                    } else {
                        boolean priorityMatch = rule.getPriorities().stream().anyMatch(item -> item != null && item == alert.getPriority());
                        return priorityMatch && !difMap.isEmpty();
                    }
                }).map(NoticeRule::getReceiverId)
                .collect(Collectors.toSet());

        receiverIds.addAll(receiverIdsByMatch);
        return noticeReceiverDao.findAllById(receiverIds);
    }

    @Override
    public NoticeReceiver getReceiverById(Long receiverId) {
        return noticeReceiverDao.getOne(receiverId);
    }

    @Override
    public NoticeRule getNoticeRulesById(Long ruleId) {
        return noticeRuleDao.getOne(ruleId);
    }

    @Override
    public boolean sendTestMsg(NoticeReceiver noticeReceiver) {
        Alert alert = new Alert();
        alert.setTarget(ALERT_TEST_TARGET);
        alert.setContent(ALERT_TEST_CONTENT);
        alert.setTimes(1);
        alert.setFirstTriggerTime(System.currentTimeMillis());
        alert.setLastTriggerTime(System.currentTimeMillis());
        alert.setPriority(CommonConstants.ALERT_PRIORITY_CODE_CRITICAL);
        return dispatcherAlarm.sendNoticeMsg(noticeReceiver, alert);
    }
}
