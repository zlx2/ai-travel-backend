package com.sora.aitravel.workflowtest;

import com.sora.aitravel.entity.Note;
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.mapper.NoteMapper;
import com.sora.aitravel.mapper.SysUserMapper;
import com.sora.aitravel.service.NoteService;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootTest
public class mybatisplus {
    @Autowired
    private NoteMapper noteMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Data
    public class NoteVO {
        private Long id;
        private String title;
        private String userName;
    }
    @Test
    void test(){
        List<Note> list=noteMapper.selectList(null);
        System.out.println("ces----------"+list);
        Set<Long> userIds = list.stream()
                .map(Note::getUserId)
                .collect(Collectors.toSet());
        System.out.println("userid-----------"+userIds);
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        System.out.println("user---"+users);
        Map<Long, SysUser> userMap = users.stream()
                .collect(Collectors.toMap(
                        SysUser::getId,
                        Function.identity()
                ));
        System.out.println("usermap-----"+  userMap);
        List<NoteVO> result = new ArrayList<>();
        for (Note note : list) {
            System.out.println(note+"列表循环数据");
            NoteVO vo = new NoteVO();
            // ① note自己的字段
            vo.setId(note.getId());
            vo.setTitle(note.getTitle());
            // ② 关键：通过 userId 找用户
            SysUser user = userMap.get(note.getUserId());
            // ⚠️ 防空（很重要）
            if (user != null) {
                vo.setUserName(user.getUsername());
            }
            result.add(vo);
        }
        System.out.println("用户！！！！！！！！！！"+result+"---------------------");
    }
}
