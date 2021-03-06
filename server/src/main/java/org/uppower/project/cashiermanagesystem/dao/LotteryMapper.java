package org.uppower.project.cashiermanagesystem.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.uppower.project.cashiermanagesystem.model.entity.LotteryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.uppower.project.cashiermanagesystem.model.dto.LotteryDto;

import java.util.List;

/**
 * <p>
 * 抽奖内容 Mapper 接口
 * </p>
 *
 * @author tuqikang
 * @since 2019-10-13
 */
public interface LotteryMapper extends BaseMapper<LotteryEntity> {

    IPage<LotteryDto> index(Page page);

    Integer isShow(@Param("id") Integer id, @Param("isShow") Integer isShow);
}
