package org.uppower.project.cashiermanagesystem.service;

import cn.windyrjc.utils.copy.DataUtil;
import cn.windyrjc.utils.response.Response;
import cn.windyrjc.utils.response.ResponsePage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.uppower.project.cashiermanagesystem.CashierManageSystemApplication;
import org.uppower.project.cashiermanagesystem.dao.RulesMapper;
import org.uppower.project.cashiermanagesystem.dao.UsersMapper;
import org.uppower.project.cashiermanagesystem.exceptions.ServerException;
import org.uppower.project.cashiermanagesystem.model.UserInfo;
import org.uppower.project.cashiermanagesystem.model.dto.UserDiscountDto;
import org.uppower.project.cashiermanagesystem.model.entity.UsersEntity;
import org.uppower.project.cashiermanagesystem.model.enums.DiscountAuthEnum;
import org.uppower.project.cashiermanagesystem.model.result.RulesResult;
import org.uppower.project.cashiermanagesystem.model.result.UserDiscountResult;
import org.uppower.project.cashiermanagesystem.model.result.UserResult;
import org.uppower.project.cashiermanagesystem.model.vo.UserRegisterVo;
import org.uppower.project.cashiermanagesystem.utils.MoneyManageUtil;
import org.uppower.project.cashiermanagesystem.utils.OrderNumberGenerator;
import org.uppower.project.cashiermanagesystem.utils.redis.RedisTemplateService;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * create by:
 * *      ____        ___  ___       __          __
 * *    /  _  \     /   |/   |      | |        / /
 * *   | | | |     / /|   /| |     | |  __   / /
 * *  | | | |     / / |__/ | |    | | /  | / /
 * * | |_| |_    / /       | |   | |/   |/ /
 * * \_______|  /_/        |_|  |___/|___/
 *
 * @date 2019/10/2112:43
 */
@Service
public class UserService {


    @Autowired
    RedisTemplateService redisTemplateService;

    @Autowired
    RulesMapper rulesMapper;

    @Autowired
    UsersMapper usersMapper;

    @Autowired
    RulesService rulesService;

    private Pattern regex = Pattern.compile("^1[345789]\\d{9}$");

    private Object lock = new Object();

    private static final String RULE_KEY = "userRole";

    private static final Integer EXPIRE = 60 * 60 * 2;

    @Transactional(rollbackFor = Exception.class)
    public Response<UserResult> index(UserInfo userInfo) {

        UserResult userResult = usersMapper.show(userInfo.getUserId());
        RulesResult rulesResult;
        if ((rulesResult = redisTemplateService.get(RULE_KEY, RulesResult.class)) == null) {
            synchronized (lock) {
                if ((rulesResult = redisTemplateService.get(RULE_KEY, RulesResult.class)) == null) {
                    rulesResult = rulesMapper.index();
                    redisTemplateService.set(RULE_KEY, rulesResult, EXPIRE);
                }
            }
        }
        if (userResult.getExperience() >= 0 && userResult.getExperience() < rulesResult.getBronze()) {
            userResult.setGrade("青铜");
        } else if (userResult.getExperience() >= rulesResult.getBronze() && userResult.getExperience() < rulesResult.getSilver()) {
            userResult.setGrade("白银");
        } else if (userResult.getExperience() >= rulesResult.getSilver() && userResult.getExperience() < rulesResult.getGold()) {
            userResult.setGrade("黄金");
        } else if (userResult.getExperience() >= rulesResult.getGold() && userResult.getExperience() < rulesResult.getPlatinum()) {
            userResult.setGrade("铂金");
        } else if (userResult.getExperience() >= rulesResult.getPlatinum() && userResult.getExperience() < rulesResult.getDiamond()) {
            userResult.setGrade("钻石");
        } else if (userResult.getExperience() >= rulesResult.getDiamond()) {
            userResult.setGrade("王者");
        }

        return Response.success(userResult);
    }

    public ResponsePage<UserDiscountResult> getDiscount(UserInfo userInfo, Integer pn, Integer status) {
        Page page = new Page(pn, CashierManageSystemApplication.PAGESIZE);
        IPage<UserDiscountDto> userDiscountDtos = usersMapper.getDiscount(page,userInfo.getUserId(),status);
        List<UserDiscountResult> userDiscountResults = null;
        userDiscountResults = userDiscountDtos.getRecords().stream().map(p->{

            UserDiscountResult result = DataUtil.convert(p,UserDiscountResult.class);
            result.setDiscount(MoneyManageUtil.fenToYuan(p.getDiscount()));
            if (result.getPattern() - DiscountAuthEnum.FULL.getAuth() == 0) {
                result.setName(DiscountAuthEnum.FULL.getName());
            } else {
                result.setName(DiscountAuthEnum.DISCOUNTS.getName());
            }
            return result;
        }).collect(Collectors.toList());
        System.out.println(userInfo.getUserId());
        return ResponsePage.success(userDiscountResults,userDiscountDtos.getPages(),userDiscountDtos.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public Response register(String openId, UserRegisterVo vo) {
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("open_id", openId);
        UsersEntity usersEntity = usersMapper.selectOne(queryWrapper);
        if (usersEntity.getVipcode() != null) {
            throw new ServerException("你已经是我们的会员了，不用注册了");
        }
        if (!regex.matcher(vo.getPhone()).matches()) {
            throw new ServerException("电话号码不合法,请确保准确");
        }
        String vipCode = OrderNumberGenerator.getVipCode();
        int success = usersMapper.registerByOpenId(openId, vo, vipCode);
        if (success != 1) {
            throw new ServerException("注册失败，服务器错误");
        }
        return Response.success();
    }

    public Response update(String openId, UserRegisterVo vo) {

        if (vo.getPhone() != null) {
            if (!regex.matcher(vo.getPhone()).matches()) {
                throw new ServerException("电话号码不合法,请确保准确");
            }
        }
        int success = usersMapper.updateByOpenId(openId, vo);
        if (success != 1) {
            throw new ServerException("修改，服务器错误");
        }
        return Response.success();
    }

    public Response vip(UserInfo userInfo) {
        return Response.success(usersMapper.selectVipById(userInfo.getUserId()));
    }
}
