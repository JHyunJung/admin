# 레거시 MyBatis 매퍼 쿼리 정리

레거시 FIDO 관리자 시스템의 MyBatis 매퍼 XML 7종을 정리한 문서다.
화면 녹화 영상(`IMG_9137.MOV`, 112초)에서 프레임을 추출해 판독했으므로,
**원본 파일이 아니라 화면에 보인 내용의 전사본**이다.

- 출처: 영상 촬영본 (Eclipse 편집기 화면)
- 판독일: 2026-09-26
- 총 쿼리 수: 약 106개 (`<sql>` 조각 제외)

> **주의**
> 눈으로 읽어 옮긴 내용이라 오탈자 가능성이 있다.
> 실제 이관·재작성 시에는 원본 XML을 다시 확인할 것.
> 확보하지 못한 구간은 각 절 말미에 명시했다.

## 목차

| 파일 | namespace | 줄 수 | 쿼리 수 | 주요 대상 테이블 |
|---|---|---|---|---|
| [basic.xml](#basicxml) | `basic` | 335 | 24 | `CCFA_ERROR_TABLE`, `CCFA_OPTION`, `CCFA_FIELDS`, `CCFA_COMPANY_AAID`, `CCFA_SYSTEM_PROP`, `CCFA_LICENSE` |
| [dashboard.xml](#dashboardxml) | `dashboard` | 72 | — | `CCFA_DASHBOARD` |
| [fds.xml](#fdsxml) | `fds` | 153 | 7 | `CCFA_FDS_POLICY`, `FIDO_LOGS`, `CCFA_FIDO_TLOG` |
| [log.xml](#logxml) | `log` | 153 | 15 | `FIDO_LOGS`, `FIDO_LOGS_BAK`, `CCFA_AUDIT_LOG`, `FIDO_STATISTICS` |
| [manager.xml](#managerxml) | `manager` | 223 | 20 | `CCFA_MANAGER`, `CCFA_STATISTICS`, `CCFA_COMPANY`, `CCFA_MANAGER_PW_POLICY` |
| [scheduler.xml](#schedulerxml) | `scheduler` | 79 | 11 | `CCFA_COMPANY`, `FIDO_STATISTICS`, `CCFA_EXCEPTIONS`, `CCFA_MAILING`, `CHALLENGE`, `TRANSACTIONHASH` |
| [statistics.xml](#statisticsxml) | `statistics` | 218 | 14 | `CCFA_STATISTICS`, `CCFA_STATISTICS_FILTER`, `CCFA_STATISTICS_ORDER`, `CCFA_MENU`, `CCFA_FIELDS` |

## 공통 패턴

여러 매퍼에서 반복되는 관례다.

### 페이징

Oracle `rownum` 기반 3중 중첩 서브쿼리를 쓴다.

```sql
select c.* from (
    select rownum as RN, b.* from (
        -- 실제 조회 + order by
    ) b
) c
<if test=" offset != null and limit != null ">
    where RN between ${offset} and (${offset} + ${limit})
</if>
```

`log.getList`만 `${offset}-1`로 시작하고, `basic.selectSystemProp`은 `1+${offset}`으로 시작한다. 나머지는 `${offset}`을 그대로 쓴다.

### 동적 필터

`basic.xml`의 `cfilter` / `cfilters` `<sql>` 조각을 `<include refid="basic.cfilters" />`로 가져다 쓴다.
`statistics.xml`은 컬럼명 키가 달라(`COLUMN_NAME`/`OP`/`VALUE`) 자체 `cfilter` / `cfilters`를 따로 갖는다.

### 파라미터 바인딩

- `#{}` — PreparedStatement 바인딩
- `${}` — 문자열 치환 (테이블명·컬럼명·offset/limit 등)

`${}`가 사용자 입력 경로에 노출되면 SQL 인젝션 위험이 있다. 이관 시 점검 대상이다.

### 로그 테이블 분할

`FIDO_LOGS_${RETURNDATE}` / `FIDO_LOGS_${item}` 형태로 날짜별 분할 테이블을 쓰며,
여러 날짜를 조회할 때는 `<foreach separator="UNION ALL">`로 이어 붙인다.

## basic.xml

`namespace="basic"` · 335줄 · 공통 CRUD와 필터 조각을 담은 핵심 매퍼

`resultMap id="hashMap"` (`java.util.HashMap`, `autoMapping="true"`)가 1~26줄에 정의돼 있다.

### getErrorInfo

```xml
<select id="getErrorInfo" parameterType="java.lang.String" resultType="java.util.HashMap" flushCache="true">
    select * from CCFA_ERROR_TABLE where ERROR_CODE = #{errorCode}
</select>
```

### getOption

```xml
<select id="getOption" resultType="java.util.HashMap" flushCache="true">
    select * from CCFA_OPTION
</select>
```

### getOptions

```xml
<select id="getOptions" resultType="java.util.HashMap" flushCache="true">
    select * from CCFA_OPTIONS where OPTION_IDX = ${OPTION_IDX}
</select>
```

### getFields

```xml
<select id="getFields" parameterType="java.util.HashMap" resultType="java.util.HashMap" flushCache="true">
    select * from CCFA_FIELDS where FIELD_TABLE = (select TBL_NAME from CCFA_MENU where MENU_CODE = #{moduleName})
    order by IDX
</select>
```

### getCVFields

```xml
<select id="getCVFields" parameterType="java.util.HashMap" resultType="java.util.HashMap" flushCache="true">
    select a.*, b.FIELD_NAME, b.FIELD_TYPE, b.FIELD_TITLE, b.OPTION_IDX, b.EDITABLE, b.PK
    from CCFA_CVFIELDS a, CCFA_FIELDS b
    where
        a.FIELD_IDX = b.IDX and CVIEW_IDX = (select IDX from CCFA_CVIEW where auth = 'public' and MENU_IDX = (select IDX from CCFA_MENU where MENU_CODE = #{MENU_CODE})) and rown…
        and a.VISIBLE = 'V'
    order by a.FIELD_SEQ
</select>
```

> 50번 줄이 화면 밖으로 잘렸다. `and rown…` 이후는 확인 불가.

### selectCompanyAAID

```xml
<select id="selectCompanyAAID" parameterType="java.util.HashMap" resultType="java.util.HashMap">
    select * from CCFA_COMPANY_AAID
    where COMPANY_IDX = ${COMPANY_IDX}
    <if test=" AAID != null ">and AAID = #{AAID}</if>
</select>
```

### disableCompanyAAID

```xml
<insert id="disableCompanyAAID" parameterType="java.util.HashMap">
    insert into CCFA_COMPANY_AAID (COMPANY_IDX, AAID) values (${COMPANY_IDX}, #{AAID})
</insert>
```

### disableCompanyAllAAID

```xml
<insert id="disableCompanyAllAAID" parameterType="java.util.HashMap">
    insert into CCFA_COMPANY_AAID (COMPANY_IDX, AAID) select ${COMPANY_IDX}, AAID from CRITERIA
</insert>
```

### enableCompanyAAID

```xml
<delete id="enableCompanyAAID" parameterType="java.util.HashMap">
    delete from CCFA_COMPANY_AAID where COMPANY_IDX = ${COMPANY_IDX}
    <if test=" AAID != null ">and AAID = #{AAID}</if>
</delete>
```

> `disable*`이 insert, `enable*`이 delete다. 이 테이블은 **차단 목록**으로 동작한다.

### getList

테이블명을 `${TBL_NAME}`으로 받아 동작하는 범용 목록 조회다.

```xml
<select id="getList" parameterType="java.util.HashMap" resultMap="hashMap" flushCache="true">
    select c.* from (
        select rownum as RN, b.* from (
            <if test=" TBL_NAME == 'CRITERIA' ">
                <if test=" COMPANY_IDX != null ">
                    select decode(b.AAID, null, 't', 'f') as "ENABLE", a.* from CRITERIA a
                    left join CCFA_COMPANY_AAID b on b.COMPANY_IDX = ${COMPANY_IDX} and a.AAID = b.AAID
                </if><if test=" COMPANY_IDX == null ">
                    select a.* from ${TBL_NAME} a
                </if>
            </if><if test=" TBL_NAME == 'FIDO_LOGS' ">
            select a.* from (
                <foreach collection="dates" item="item" separator="UNION ALL">
                    select * from FIDO_LOGS_${item} WHERE 1=1
                </foreach>
                ) a
            </if><if test=" TBL_NAME != 'CRITERIA' and TBL_NAME != 'FIDO_LOGS'">
                select a.* from ${TBL_NAME} a
            </if>
            <include refid="cfilters" />
            order by
            <choose>
                <when test=" TBL_NAME == 'CCFA_FIDO_TLOG' "> a.CREATEDTIME desc </when>
                <otherwise> a.IDX desc </otherwise>
            </choose>
        ) b
    ) c
    <if test=" offset != null and limit != null ">
        where RN between ${offset} and (${offset} + ${limit})
    </if>
</select>
```

### getChildList

```xml
<select id="getChildList" parameterType="java.util.HashMap" resultMap="hashMap" flushCache="true">
    select a.* from ${TBL_NAME} a
    where a.${PARENT_COL_NM} = #{PARENT_VALUE}
    order by a.${SORT_COL_NM} desc<if test="SUB_SORT != null">, ${SUB_SORT} desc</if>
</select>
```

### getTotalCount

```xml
<select id="getTotalCount" parameterType="java.util.HashMap" resultType="java.lang.Integer" flushCache="true">
    <if test=" TBL_NAME == 'FIDO_LOGS' ">
    select count(*) from (
        <foreach collection="dates" item="item" separator="UNION ALL">
            select * from FIDO_LOGS_${item}
            <include refid="cfilters" />
        </foreach>
        )
    </if>
    <if test=" TBL_NAME != 'FIDO_LOGS' ">
    select count(*) from ${TBL_NAME} <include refid="cfilters" />
    </if>
</select>
```

### getData

```xml
<select id="getData" parameterType="java.util.HashMap" resultMap="hashMap" flushCache="true">
    select a.* from ${TBL_NAME} a
    where a.IDX = #{IDX}
    <if test=" COMPANY_IDX != null "> and a.COMPANY_IDX = ${COMPANY_IDX} </if>
</select>
```

### updateData

```xml
<update id="updateData" parameterType="java.util.HashMap">
    update ${TBL_NAME} set
    <foreach collection="filters" item="item" index="index" separator=" , ">
        <if test=" item.filter != null and item.value != null ">
            <if test=" item.type == 'STRING' ">${item.filter} = #{item.value}</if>
            <if test=" item.type == 'INTEGER' ">${item.filter} = ${item.value}</if>
            <if test=" item.type == 'NOW' ">${item.filter} = sysdate</if>
        </if>
    </foreach>
    where IDX = ${IDX}
</update>
```

### deleteData

```xml
<delete id="deleteData" parameterType="java.util.HashMap">
    delete from ${TBL_NAME} where IDX in
    <foreach collection="array" item="item" index="index" separator="," open="(" close=")">
        ${array[index]}
    </foreach>
</delete>
```

### insertData

```xml
<insert id="insertData" parameterType="java.util.HashMap">
    insert into ${TBL_NAME}
    <foreach collection="filters" item="item" index="index" separator="," open="(" close=")">
        <if test=" item.filter != null and item.value != null ">
            ${item.filter}
        </if>
    </foreach>
    values
    <foreach collection="filters" item="item" index="index" separator="," open="(" close=")">
        <if test=" item.filter != null and item.value != null ">
            <if test=" item.type == 'STRING' ">#{item.value}</if>
            <if test=" item.type == 'INTEGER' ">${item.value}</if>
            <if test=" item.type == 'NOW' ">sysdate</if>
        </if>
    </foreach>
</insert>
```

### `<sql id="cfilter">` — 단일 조건 조각

연산자(`item.op`)에 따라 SQL 조건을 만들어 내는 핵심 조각이다.

```xml
<sql id="cfilter">
    <if test=" item.filter != null and item.value != null">
        ${item.filter}
        <choose>
            <when test=" item.op == 'Equal' ">
                <if test=" item.value == null "> IS NULL </if>
                <if test=" item.value != null "> = #{item.value} </if>
            </when>
            <when test=" item.op == 'NotEqual' "> != #{item.value} </when>
            <when test=" item.op == 'Like' "> LIKE ('%' || #{item.value} || '%') </when>
            <when test=" item.op == 'NotLike' "> NOT LIKE ('%' || #{item.value} || '%') </when>
            <when test=" item.op == 'StartWith' "> LIKE (#{item.value} || '%') </when>
            <when test=" item.op == 'EndWith' "> LIKE ('%' || #{item.value}) </when>
            <when test=" item.op == 'Over' "> &gt;= #{item.value} </when>
            <when test=" item.op == 'Under' "> &lt;= #{item.value} </when>
            <when test=" item.op == 'UnderTime' "> &lt;= to_timestamp(#{item.value[0]}, #{item.value[1]}) </when>
            <when test=" item.op == 'Bigger' "> &gt; #{item.value} </when>
            <when test=" item.op == 'Smaller' "> &lt; #{item.value} </when>
            <when test=" item.op == 'Null' "> IS NULL </when>
            <when test=" item.op == 'NotNull' "> IS NOT NULL </when>
            <when test=" item.op == 'In' "> IN <foreach collection="item.value" item="it" index="index" separator="," open="(" close=")">${it}</foreach> </when>
            <when test=" item.op == 'InString' "> to_char(${category}, 'YYYY-MM-DD HH24:MI:SS') </when>
            <when test=" item.op == 'NotIn' "> NOT IN <foreach collection="item.value" item="it" index="index" separator="," open="(" close=")">${it}</foreach> </when>
            <when test=" item.op == 'TRUE' "> IS TRUE </when>
            <when test=" item.op == 'FALSE' "> IS FALSE </when>
            <when test=" item.op == 'Between' ">
                <choose>
                    <when test=" item.filter == 'CREATEDTIME' ">BETWEEN to_timestamp(#{item.value[0]}, 'YYYY-MM-DD HH24:MI:SS') AND to_timestamp(#{item.value[1]}, 'YYYY-MM-DD H…</when>
                    <when test=" item.filter == 'UPDATEDTIME' ">BETWEEN to_timestamp(#{item.value[0]}, 'YYYY-MM-DD HH24:MI:SS') AND to_timestamp(#{item.value[1]}, 'YYYY-MM-DD H…</when>
                    <when test=" item.filter == 'CREATETIME' ">BETWEEN to_timestamp(#{item.value[0]}, 'YYYY-MM-DD HH24:MI:SS') AND to_timestamp(#{item.value[1]}, 'YYYY-MM-DD HH…</when>
                    <otherwise>BETWEEN #{item.value[0]} AND #{item.value[1]}</otherwise>
                </choose>
            </when>
        </choose>
    </if>
</sql>
```

> `Between` 분기의 세 줄은 화면 밖으로 잘려 끝부분이 보이지 않는다.
> 패턴상 `'YYYY-MM-DD HH24:MI:SS')` 로 끝날 것으로 보인다.

### `<sql id="cfilters">` — 조건 묶음 조각

```xml
<sql id="cfilters">
    <if test=" filters != null ">
        <trim prefix="WHERE" prefixOverrides="AND |OR ">
            <foreach collection="filters" item="item" index="index" separator=" OR ">
                <include refid="basic.cfilter" />
            </foreach>
        </trim>
    </if>
    <if test=" ANDfilters != null ">
        <trim prefix="WHERE" prefixOverrides="AND |OR ">
            <foreach collection="ANDfilters" item="item" index="index" separator=" AND ">
                <include refid="basic.cfilter" />
            </foreach>
        </trim>
    </if>
</sql>
```

> `filters`는 OR, `ANDfilters`는 AND로 묶인다. 둘 다 있으면 `WHERE`가 두 번 나온다.

### selectSystemProp

```xml
<select id="selectSystemProp" resultType="java.util.HashMap" flushCache="true">
    select c.* from (
        select rownum as RN, b.* from (
            select a.* from CCFA_SYSTEM_PROP a
            <include refid="cfilters" />
        ) b
    ) c <if test=" offset != null and limit != null ">
        where RN between (1+${offset}) and (${offset} + ${limit})
    </if>
</select>
```

### updateSystemProp

```xml
<update id="updateSystemProp" parameterType="java.util.HashMap">
    update CCFA_SYSTEM_PROP set
        PROP_VALUE = #{PROP_VALUE},
        UPDATEDTIME = sysdate
    where COMPANY_IDX = ${COMPANY_IDX} and PROP_KEY = #{PROP_KEY} <if test=" COMPANY_IDX != 0 "> and SHARE_TYPE = 'NO'</if>
</update>
```

### insertSystemProp

```xml
<insert id="insertSystemProp" parameterType="java.util.HashMap">
    INSERT INTO CCFA_SYSTEM_PROP
        (PROP_KEY,
         PROP_VALUE,
         COMPANY_IDX,
         UPDATEDTIME,
         SHARE_TYPE)
    VALUES(
        #{PROP_KEY},
        #{PROP_VALUE},
        #{COMPANY_IDX},
        sysdate,
        'NO'
        )
</insert>
```

### selectLicense

```xml
<select id="selectLicense" parameterType="java.util.HashMap" resultType="java.util.HashMap" flushCache="true">
    select c.* from (
        select rownum as RN, b.* from (
            select a.* from CCFA_LICENSE a
            <include refid="cfilters" />
        ) b
    ) c <if test=" offset != null and limit != null ">
        where RN between ${offset} and (${offset} + ${limit})
    </if>
</select>
```

### getLicenseTotalCount

```xml
<select id="getLicenseTotalCount" parameterType="java.util.HashMap" resultType="java.lang.Integer" flushCache="true">
    select count(*) from CCFA_LICENSE a
    <include refid="cfilters" />
</select>
```

### insertLicense

```xml
<insert id="insertLicense" parameterType="java.util.HashMap">
    insert into CCFA_LICENSE (COMPANY_IDX, COMPANY_NAME, CONTACT_NAME, CONTACT_PHONE, CONTACT_EMAIL, SERVICE_NAME, ETC, LICENSE, FILE_PATH, HASHVALUE) values (
        ${COMPANY_IDX},
        (select COMPANY_NAME from CCFA_COMPANY where IDX = ${COMPANY_IDX}),
        #{CONTACT_NAME, jdbcType=VARCHAR},
        #{CONTACT_PHONE, jdbcType=VARCHAR},
        #{CONTACT_EMAIL, jdbcType=VARCHAR},
        #{SERVICE_NAME, jdbcType=VARCHAR},
        #{ETC, jdbcType=VARCHAR},
        #{LICENSE, jdbcType=VARCHAR},
        #{FILE_PATH, jdbcType=VARCHAR},
        #{HASHVALUE, jdbcType=VARCHAR}
        )
</insert>
```

### updateLicense

```xml
<update id="updateLicense" parameterType="java.util.HashMap">
    update CCFA_LICENSE set
        COMPANY_IDX = ${COMPANY_IDX},
        COMPANY_NAME = (select COMPANY_NAME from CCFA_COMPANY where IDX = ${COMPANY_IDX}),
        CONTACT_NAME = #{CONTACT_NAME, jdbcType=VARCHAR},
        CONTACT_PHONE = #{CONTACT_PHONE, jdbcType=VARCHAR},
        CONTACT_EMAIL = #{CONTACT_EMAIL, jdbcType=VARCHAR},
        SERVICE_NAME = #{SERVICE_NAME, jdbcType=VARCHAR},
        ETC = #{ETC, jdbcType=VARCHAR},
        LICENSE = #{LICENSE, jdbcType=VARCHAR},
        FILE_PATH = #{FILE_PATH, jdbcType=VARCHAR},
        HASHVALUE = #{HASHVALUE, jdbcType=VARCHAR},
        UPDATEDTIME = sysdate
    where IDX = ${IDX}
</update>
```

### deleteLisense

> 원본 오타 그대로다 (`Lisense`).

```xml
<delete id="deleteLisense" parameterType="java.util.HashMap">
    delete from CCFA_LICENSE where IDX in
    <foreach collection="array" item="item" index="index" separator="," open="(" close=")">
        ${array[index]}
    </foreach>
</delete>
```

## dashboard.xml

`namespace="dashboard"` · 72줄 · 대시보드 레이아웃(`CCFA_DASHBOARD`) 관리

영상에서 전체가 한 화면에 노출됐으나, 이 매퍼만 다른 파일들에 비해 짧고 쿼리 ID가 화면에서 선명히 구분되지 않았다. 이관 시 원본 확인이 필요하다.

## fds.xml

`namespace="fds"` · 153줄 · FDS(이상거래 탐지) 정책과 모니터링

`resultMap id="hashMap"` (1~13줄)이 앞에 정의돼 있다.

### insert

```xml
<insert id="insert" parameterType="java.util.HashMap">
    insert into CCFA_FDS_POLICY
    (COMPANY_IDX, AND_COUNTRY, OR_COUNTRY) values (${COMPANY_IDX}, 'NO',
    'NO')
</insert>
```

### update

```xml
<update id="update" parameterType="java.util.HashMap">
    update CCFA_FDS_POLICY set
        AND_IP = #{AND_IP, jdbcType=VARCHAR},
        AND_TERM = #{AND_TERM, jdbcType=VARCHAR},
        AND_DEVICE = #{AND_DEVICE, jdbcType=VARCHAR},
        AND_COUNTRY = #{AND_COUNTRY, jdbcType=VARCHAR},
        OR_IP = #{OR_IP, jdbcType=VARCHAR},
        OR_TERM = #{OR_TERM, jdbcType=VARCHAR},
        OR_COUNTRY = #{OR_COUNTRY, jdbcType=VARCHAR},
        UPDATEDTIME = sysdate
    where COMPANY_IDX = ${COMPANY_IDX}
</update>
```

### delete

```xml
<delete id="delete" parameterType="java.util.HashMap">
    delete from CCFA_FDS_POLICY
    where COMPANY_IDX = ${COMPANY_IDX}
</delete>
```

### `<sql id="FDSMonitor">` — FDS 탐지 조건 조각

이 매퍼의 핵심이다. AND 조건군과 OR 조건군을 각각 `<trim>`으로 묶는다.

```xml
<sql id="FDSMonitor">
    select
    a.*
    from (
    select * from FIDO_LOGS_${RETURNDATE}
    where CREATEDTIME between to_timestamp(#{dterm[0]}, 'YYYY-MM-DD
    HH24:MI:SS') AND to_timestamp(#{dterm[1]}, 'YYYY-MM-DD HH24:MI:SS')
    ) a
    <trim prefix="WHERE" prefixOverrides="AND |OR ">
        a.COMPANY_IDX = ${COMPANY_IDX}
        <if
            test=" AND_DEVICE != null and AND_IP != null or AND_TERM != null or AND_COUNTRY != 'NO' ">
            <trim prefix=" AND " prefixOverrides="AND |OR ">
                <if test=" AND_IP != null ">
                    <foreach collection="AND_IP" item="item" index="index"
                        separator=" OR " open="(" close=")">
                        a.LONGIP between ${item[0]} and
                        ${item[1]}
                    </foreach>
                </if>

                <if test=" AND_DEVICE == 'Android' ">
                    AND ( instr(a.UA, 'android', 1) + instr(a.UA, 'java',
                    1) ) &gt; 0
                </if>
                <if test=" AND_DEVICE == 'iOS' ">
                    AND 1 &gt; ( instr(a.UA, 'android', 1) + instr(a.UA,
                    'java', 1) )
                </if>

                <if test=" AND_COUNTRY == 'KR' ">
                    AND a.COUNTRY = 'KR'
                </if>
                <if test=" AND_COUNTRY == 'NOKR' ">
                    AND a.COUNTRY != 'KR'
                </if>
                <if
                    test=" AND_TERM != null and AND_TERM != '0' and AND_TERM != 0 ">
                    AND (
                    select count(x.CREATEDTIME) - 1 from CCFA_FIDO_TLOG x
                    where x.ACCESSIP = a.ACCESSIP and x.USERNAME = a.USERNAME and
                    x.COMPANY_IDX = a.COMPANY_IDX and
                    x.CREATEDTIME between (a.CREATEDTIME - INTERVAL '${AND_TERM}' SECOND) and
                    (a.CREATEDTIME + INTERVAL '${AND_TERM}' SECOND)
                    ) &gt; 0
                </if>
            </trim>
        </if>

        <if
            test=" OR_IP != null or OR_TERM != null or OR_COUNTRY != 'NO' ">
            <trim prefix=" OR " prefixOverrides="AND |OR ">
                <if test=" OR_IP != null ">
                    <foreach collection="AND_IP" item="item" index="index"
                        separator=" OR " open="(" close=")">
                        a.LONGIP between ${item[0]} and
                        ${item[1]}
                    </foreach>
                </if>
                <if test=" OR_TERM != null and OR_TERM != '0' and OR_TERM != 0 ">
                    OR (
                    select count(x.CREATEDTIME) - 1 from CCFA_FIDO_TLOG x
                    where x.ACCESSIP = a.ACCESSIP and x.USERNAME = a.USERNAME and
                    x.CREATEDTIME between (a.CREATEDTIME - INTERVAL '${OR_TERM}'
                    SECOND) and (a.CREATEDTIME + INTERVAL '${OR_TERM}' SECOND)
                    ) &gt; 0
                </if>
                <if test=" OR_COUNTRY == 'KR' ">
                    OR a.COUNTRY = 'KR'
                </if>
                <if test=" OR_COUNTRY == 'NOKR' ">
                    OR a.COUNTRY != 'KR'
                </if>
            </trim>
        </if>
    </trim>
</sql>
```

> **버그로 보이는 지점**: OR 조건군의 `OR_IP != null` 분기에서 순회 대상이
> `collection="AND_IP"`다. `OR_IP`여야 할 것으로 보인다.
>
> 또한 AND 쪽 `AND_TERM` 서브쿼리에는 `x.COMPANY_IDX = a.COMPANY_IDX` 조건이 있는데
> OR 쪽 `OR_TERM`에는 없다.

### selectRealTimeC1

```xml
<select id="selectRealTimeC1" parameterType="java.util.HashMap"
    resultType="java.util.HashMap">
    select TYPE, count(*) COUNT
    from CCFA_FIDO_TLOG
    WHERE to_char(CREATEDTIME, 'yyyy-mm-dd hh24:mi:ss') =
    to_char(sysdate - INTERVAL '1' SECOND, 'yyyy-mm-dd hh24:mi:ss') and
    COMPANY_IDX = ${COMPANY_IDX}
    group by TYPE
</select>
```

### selectRecentLogId

```xml
<select id="selectRecentLogId" parameterType="java.util.HashMap">
    select MAX(IDX)
    from CCFA_FIDO_TLOG
    where COMPANY_IDX
</select>
```

> `where COMPANY_IDX` 뒤가 비어 있다. 원본에서 미완성이거나 판독 누락일 수 있다.

### selectFDSMonitorTotalCount

```xml
<select id="selectFDSMonitorTotalCount"
    parameterType="java.util.HashMap" resultType="java.lang.Integer">
    select count(u.CREATEDTIME) from (
    <include refid="FDSMonitor" />
    ) u
</select>
```

### selectFDSMonitor

```xml
<select id="selectFDSMonitor" parameterType="java.util.HashMap"
    resultType="java.util.HashMap">
    select c.* from (
    select rownum as RN, b.* from (
    <include refid="FDSMonitor" />
    order by a.CREATEDTIME desc
    ) b
    ) c
    <if test=" offset != null and limit != null ">
        where RN between ${offset} and (${offset} + ${limit})
    </if>
</select>
```

**확보하지 못한 부분**: 1~3줄 (XML 선언·DOCTYPE·mapper 태그)

## log.xml

`namespace="log"` · 153줄 · FIDO 인증 로그와 감사 로그

```xml
<resultMap id="hashMap" type="java.util.HashMap" autoMapping="true">
    <result column="JSONDATA" property="JSONDATA" jdbcType="CLOB" javaType="java.lang.String"/>
</resultMap>
```

### getIndexList

```xml
<select id="getIndexList" parameterType="java.util.HashMap" resultType="java.lang.Integer" flushCache="true">
    SELECT
        t.IDX
    FROM FIDO_LOGS_${RETURNDATE} t
    WHERE 1=1
        and COMPANY_IDX = ${COMPANY_IDX}
        and t.CREATEDTIME &gt;= to_timestamp(#{STARTTIME}, 'YYYY-MM-DD HH24')
        and t.CREATEDTIME &lt; to_timestamp(#{ENDTIME}, 'YYYY-MM-DD HH24')
</select>
```

### getJsonList

```xml
<select id="getJsonList" parameterType="java.lang.Integer" resultMap="hashMap" flushCache="true">
    SELECT
        to_char(t.CREATEDTIME, 'yyyy/MM/dd/hh24') as "CFL",
        t.SERVICENAME,
        t.JSONDATA
    FROM FIDO_LOGS_${RETURNDATE} t
    where IDX IN <foreach collection="TARGETLOGS" item="it" index="index" separator="," open="(" close=")">${it}</foreach>
</select>
```

### getJsonList2

```xml
<select id="getJsonList2" parameterType="java.lang.Integer" resultMap="hashMap" flushCache="true">
    SELECT
        to_char(t.CREATEDTIME, 'yyyy/MM/dd/hh24') as "CFL",
        t.SERVICENAME,
        t.JSONDATA
    FROM FIDO_LOGS_BAK t
    where IDX IN <foreach collection="List" item="it" index="index" separator="," open="(" close=")">${it}</foreach>
</select>
```

### deleteListIndex

```xml
<delete id="deleteListIndex" parameterType="java.lang.Integer">
    delete from FIDO_LOGS
    where IDX IN <foreach collection="list" item="it" index="index" separator="," open="(" close=")">${it}</foreach>
</delete>
```

### getList

```xml
<select id="getList" parameterType="java.util.HashMap" resultMap="hashMap" flushCache="true">
    select c.* from (
        select rownum as RN, a.* from (
            SELECT
                to_char(t.CREATEDTIME, 'yyyy-MM-dd hh24:mi:ss') as "TARGET",
                to_char(t.CREATEDTIME, 'yyyy/MM/dd/hh24') as "CFL",
                t.IDX,
                t.COMPANY_IDX,
                t.SERIALCODE,
                t.SERVICENAME,
                t.JSONDATA,
                t.CREATEDTIME
            FROM FIDO_LOGS_${RETURNDATE} t
            <if test=" COMPANY_IDX != null ">WHERE t.COMPANY_IDX = #{COMPANY_IDX}</if>
            ORDER BY t.CREATEDTIME desc, t.IDX desc
        ) a
        <include refid="basic.cfilters" />
    ) c
    <if test=" offset != null and limit != null ">
        where RN between ${offset} and (${offset}-1 + ${limit})
    </if>
</select>
```

### deleteTargetLogs

```xml
<delete id="deleteTargetLogs" parameterType="java.util.HashMap">
    delete from FIDO_LOGS
    where COMPANY_IDX = ${COMPANY_IDX} and ( CREATEDTIME &lt;= to_timestamp(#{TARGET}, 'YYYY-MM-DD HH24') )
</delete>
```

### getTotalCount

```xml
<select id="getTotalCount" parameterType="java.util.HashMap" resultType="java.lang.Integer" flushCache="true">
    select count(a.IDX) from (
        <foreach collection="dates" item="item" separator="UNION ALL">
            SELECT
                to_char(t.CREATEDTIME, 'yyyy-MM-dd hh24:mi:ss') as "TARGET",
                t.IDX,
                t.COMPANY_IDX,
                t.CREATEDTIME
            FROM FIDO_LOGS_${item} t
            <if test=" COMPANY_IDX != null ">WHERE t.COMPANY_IDX = #{COMPANY_IDX}</if>
        </foreach>
        ) a
    <include refid="basic.cfilters" />
</select>
```

### getTargetList

```xml
<select id="getTargetList" parameterType="java.util.HashMap" resultType="java.lang.Integer" flushCache="true">
    select IDX from (
        SELECT
            to_char(t.CREATEDTIME, 'yyyy-MM-dd hh24:mi:ss') as "TARGET",
            t.IDX,
            t.COMPANY_IDX,
            t.CREATEDTIME
        FROM FIDO_LOGS t
        <if test=" COMPANY_IDX != null ">WHERE t.COMPANY_IDX = #{COMPANY_IDX}</if>
        ORDER BY t.CREATEDTIME desc, t.IDX desc
    ) a
    <include refid="basic.cfilters" />
</select>
```

### getTargetJson

```xml
<select id="getTargetJson" parameterType="java.util.HashMap" resultMap="hashMap" flushCache="true">
    SELECT
        JSONDATA
    FROM FIDO_LOGS
    <include refid="basic.cfilters" />
</select>
```

### getData

```xml
<select id="getData" parameterType="java.util.HashMap" resultType="java.util.HashMap" flushCache="true">
    select a.*
    from ${TBL_NAME} a
    where a.IDX = #{IDX}
</select>
```

### getItemByGroup

```xml
<select id="getItemByGroup" parameterType="java.util.HashMap" resultType="java.util.HashMap" flushCache="true">

</select>
```

> 본문이 비어 있다. 원본 그대로다.

### insertLog

```xml
<insert id="insertLog" parameterType="java.util.HashMap">
    insert into CCFA_AUDIT_LOG (USER_NAME, USER_ID, TYPE, MESSAGE, COMPANY_IDX, COMPANY_NAME, IP, UA, INTERGRITY_HASH) values (
        #{USER_NM}, #{USER_ID}, #{TYPE}, #{MESSAGE}, #{COMPANY_IDX}, #{COMPANY_NAME}, #{IP}, #{UA}, #{INTERGRITY_HASH, jdbcType=VARCHAR}
        )
</insert>
```

> 컬럼은 `USER_NAME`인데 파라미터는 `#{USER_NM}`이다. 원본 그대로다.

### selectLog

```xml
<select id="selectLog" parameterType="java.util.HashMap" resultType="java.util.HashMap" flushCache="true">
    select c.* from (
        select rownum as RN, b.* from (
            select a.* from CCFA_AUDIT_LOG a
            <include refid="basic.cfilters" />
            <if test=" offset != null and limit != null ">
                order by a.CREATEDTIME desc
            </if>
        ) b
    ) c
    <if test=" offset != null and limit != null ">
        where RN between ${offset} and (${offset} + ${limit})
    </if>
</select>
```

### selectLogTotalCount

```xml
<select id="selectLogTotalCount" parameterType="java.util.HashMap" resultType="java.lang.Integer" flushCache="true">
    select count(*) from CCFA_AUDIT_LOG a
    <include refid="basic.cfilters" />
</select>
```

### getLastStatistics

```xml
<select id="getLastStatistics" resultType="java.lang.String" flushCache="true">
    SELECT GROUPBY
    FROM (
        SELECT GROUPBY
        FROM FIDO_STATISTICS
        WHERE 1=1
        ORDER BY GROUPBY DESC
        )
    WHERE ROWNUM = 1
</select>
```

## manager.xml

`namespace="manager"` · 223줄 · 운영자 계정과 고객사 관리

### selectManagerTotalCount

```xml
<select id="selectManagerTotalCount" parameterType="java.util.HashMap" resultType="java.lang.Integer" flushCache="true">
    select count(*) from CCFA_MANAGER a
    <include refid="basic.cfilters" />
</select>
```

### insertManager

```xml
<insert id="insertManager" parameterType="java.util.HashMap">
    insert into CCFA_MANAGER (
        USER_ID, USER_PW, USER_NM, USER_EMAIL, USER_PHONE, COMPANY_IDX, STATUS, ETC, ALRAM_TYPE ,LAST_PW_CHANGE_DATE
    ) values (
        #{USER_ID, jdbcType=VARCHAR},
        #{USER_PW, jdbcType=VARCHAR},
        #{USER_NM, jdbcType=VARCHAR},
        #{USER_EMAIL, jdbcType=VARCHAR},
        #{USER_PHONE, jdbcType=VARCHAR},
        #{COMPANY_IDX, jdbcType=INTEGER},
        #{STATUS, jdbcType=VARCHAR},
        #{ETC, jdbcType=VARCHAR},
        #{ALRAM_TYPE, jdbcType=VARCHAR},
        #{LAST_PW_CHANGE_DATE}
        )
    <selectKey keyProperty="IDX" resultType="java.lang.Integer" order="AFTER">
        SELECT SEQ_CCFA_MANAGER.currval FROM dual
    </selectKey>
</insert>
```

> `ALRAM_TYPE`은 원본 오타다 (`ALARM`이 아님). 이 파일 전체에서 일관되게 쓰인다.

### managerStatisticsInit1

신규 운영자 생성 시 기본 대시보드 통계 항목을 심는 쿼리 4종 중 첫 번째다.

```xml
<insert id="managerStatisticsInit1" parameterType="java.util.HashMap">
    insert into CCFA_STATISTICS (OWNER_IDX, TYPE, TARGET_IDX, GRAPH_TYPE, TITLE, CONTENT, OPEN_TYPE, PRIME_FIELD_IDX, REALTIME) values (
        ${OWNER_IDX}, 'count', (select IDX from CCFA_MENU where MENU_CODE = 'authenticate'), 'dash', '오늘 등록 요청 수', '사용자 등록 요청 수', 'open',
        (select IDX from CCFA_FIELDS where FIELD_NAME = 'IDX' and FIELD_TABLE = 'CCFA_FIDO_TLOG'), 'today'
        )
</insert>
```

### managerStatisticsInit2

```xml
<insert id="managerStatisticsInit2" parameterType="java.util.HashMap">
    insert into CCFA_STATISTICS (OWNER_IDX, TYPE, TARGET_IDX, GRAPH_TYPE, TITLE, CONTENT, OPEN_TYPE, PRIME_FIELD_IDX, REALTIME) values (
        ${OWNER_IDX}, 'count', (select IDX from CCFA_MENU where MENU_CODE = 'authenticate'), 'dash', '이번주 요청 건수', '이번주 트랜잭션 요청 건수', 'open',
        (select IDX from CCFA_FIELDS where FIELD_NAME = 'IDX' and FIELD_TABLE = 'CCFA_FIDO_TLOG'), 'tweekly'
        )
</insert>
```

### managerStatisticsInit3

```xml
<insert id="managerStatisticsInit3" parameterType="java.util.HashMap">
    insert into CCFA_STATISTICS (OWNER_IDX, TYPE, TARGET_IDX, GRAPH_TYPE, TITLE, CONTENT, OPEN_TYPE, PRIME_FIELD_IDX, REALTIME) values (
        ${OWNER_IDX}, 'count', (select IDX from CCFA_MENU where MENU_CODE = 'fidouser'), 'dash', '총 등록자 수', '현재 시스템에 등록된 실 사용자 수', 'open',
        (select IDX from CCFA_FIELDS where FIELD_NAME = 'IDX' and FIELD_TABLE = 'USERINFO'), 'today'
        )
</insert>
```

### managerStatisticsInit4

```xml
<insert id="managerStatisticsInit4" parameterType="java.util.HashMap">
    insert into CCFA_DASHBOARD (STATISTICS_IDX, SEQ, OWNER_IDX, FLAT) select IDX, rownum, OWNER_IDX, 'ON' from CCFA_STATISTICS where OWNER_IDX = ${OWNER_IDX} order by IDX
</insert>
```

### insertSystemProp

```xml
<insert id="insertSystemProp" parameterType="java.util.HashMap">
    insert into CCFA_SYSTEM_PROP (PROP_KEY, PROP_VALUE, COMPANY_IDX) select PROP_KEY, PROP_VALUE, ${COMPANY_IDX} from CCFA_SYSTEM_PROP where SHARE_TYPE = 'NO' and COMPANY_IDX = …
</insert>
```

> 줄 끝이 화면 밖으로 잘렸다. `COMPANY_IDX = 0` 등으로 추정되나 확인 불가.

### deleteSystemProp

```xml
<insert id="deleteSystemProp" parameterType="java.util.HashMap">
    delete from CCFA_SYSTEM_PROP where SHARE_TYPE = 'NO' and COMPANY_IDX = ${COMPANY_IDX}
</insert>
```

> delete 문인데 `<insert>` 태그를 쓴다. 원본 그대로다.

### updateManager

```xml
<update id="updateManager" parameterType="java.util.HashMap">
    update CCFA_MANAGER set
        <!-- <if test=" USER_ID != null ">USER_ID = #{USER_ID},</if> -->
        <if test=" USER_PW != null ">USER_PW = #{USER_PW},</if>
        <if test=" USER_NM != null ">USER_NM = #{USER_NM},</if>
        <if test=" USER_EMAIL != null ">USER_EMAIL = #{USER_EMAIL},</if>
        <if test=" USER_PHONE != null ">USER_PHONE = #{USER_PHONE},</if>
        <if test=" COMPANY_IDX != null ">COMPANY_IDX = ${COMPANY_IDX},</if>
        <if test=" STATUS != null ">STATUS = #{STATUS},</if>
        <if test=" LOGIN != null ">LOGIN = #{LOGIN},</if>
        <if test=" BLOCK_TIME == 'now' ">BLOCK_TIME = sysdate,</if>
        <if test=" LAST_ACCESS == 'now' ">LAST_ACCESS = sysdate,</if>
        <if test=" ETC != null ">ETC = #{ETC},</if>
        <if test=" ALRAM_TYPE != null ">ALRAM_TYPE = #{ALRAM_TYPE},</if>
        <if test=" LAST_PW_CHANGE_DATE != null ">LAST_PW_CHANGE_DATE = #{LAST_PW_CHANGE_DATE},</if>
        UPDATEDTIME = sysdate
    where IDX = ${IDX}
</update>
```

> `USER_ID` 갱신은 주석 처리돼 있다. 로그인 ID는 변경 불가 정책으로 보인다.

### deleteManager

```xml
<delete id="deleteManager" parameterType="java.lang.String">
    delete from CCFA_MANAGER where IDX in
    <foreach collection="array" item="item" index="index" separator="," open="(" close=")">
        ${array[index]}
    </foreach>
</delete>
```

### deleteManagerData

```xml
<delete id="deleteManagerData" parameterType="java.lang.String">
    delete from CCFA_DASHBOARD where OWNER_IDX in
    <foreach collection="array" item="item" index="index" separator="," open="(" close=")">
        ${array[index]}
    </foreach>
</delete>
```

### selectCompany

```xml
<select id="selectCompany" parameterType="java.util.HashMap" resultType="java.util.HashMap" flushCache="true">
    select c.* from (
        select rownum as RN, b.* from (
            select a.* from CCFA_COMPANY a
            <include refid="basic.cfilters" />
            <if test=" offset != null and limit != null ">
                order by a.CREATEDTIME desc
            </if>
        ) b
    ) c
    <if test=" offset != null and limit != null ">
        where RN between ${offset} and (${offset} + ${limit})
    </if>
</select>
```

### selectCompanyTotalCount

```xml
<select id="selectCompanyTotalCount" parameterType="java.util.HashMap" resultType="java.lang.Integer" flushCache="true">
    select count(*) from CCFA_COMPANY a
    <include refid="basic.cfilters" />
</select>
```

### insertCompany

```xml
<insert id="insertCompany" parameterType="java.util.HashMap">
    insert into CCFA_COMPANY (
        COMPANY_NAME, CONTACT, CONTACT_PHONE, CONTACT_PHONE2, CONTACT_ADDR, COMPANY_TYPE, ENABLE_TYPE, STARTTIME, ENDTIME, MAX_APPID, MAX_APPSERVER, MAX_USER, CREATOR, UPDATOR,…
    ) values (
        #{COMPANY_NAME, jdbcType=VARCHAR},
        #{CONTACT, jdbcType=VARCHAR},
        #{CONTACT_PHONE, jdbcType=VARCHAR},
        #{CONTACT_PHONE2, jdbcType=VARCHAR},
        #{CONTACT_ADDR, jdbcType=VARCHAR},
        #{COMPANY_TYPE, jdbcType=VARCHAR},
        #{ENABLE_TYPE, jdbcType=VARCHAR},
        to_timestamp(#{STARTTIME}, 'YYYY-MM-DD HH24:MI:SS'),
        to_timestamp(#{ENDTIME}, 'YYYY-MM-DD HH24:MI:SS'),
        ${MAX_APPID},
        ${MAX_APPSERVER},
        ${MAX_USER},
        ${CREATOR},
        ${CREATOR},
        #{ETC, jdbcType=VARCHAR},
        #{VENDOR_CODE}
        )
    <selectKey keyProperty="IDX" resultType="java.lang.Integer" order="AFTER">
        SELECT SEQ_CCFA_COMPANY.currval FROM dual
    </selectKey>
</insert>
```

> 컬럼 목록 줄이 화면 밖으로 잘렸다. VALUES 절로 보아 `ETC`, `VENDOR_CODE`가 이어질 것으로 보인다.
> `UPDATOR` 자리에 `${CREATOR}`가 두 번 들어간다 (신규 생성이므로 의도된 것으로 보인다).

### deleteCompany

```xml
<delete id="deleteCompany" parameterType="java.lang.String">
    delete from CCFA_COMPANY where IDX in
    <foreach collection="array" item="item" index="index" separator="," open="(" close=")">
        ${array[index]}
    </foreach>
</delete>
```

### updateCompany

```xml
<update id="updateCompany" parameterType="java.util.HashMap">
    update CCFA_COMPANY set
        <if test=" COMPANY_NAME != null ">COMPANY_NAME = #{COMPANY_NAME},</if>
        <if test=" CONTACT != null ">CONTACT = #{CONTACT},</if>
        <if test=" CONTACT_PHONE != null ">CONTACT_PHONE = #{CONTACT_PHONE},</if>
        <if test=" CONTACT_PHONE2 != null ">CONTACT_PHONE2 = #{CONTACT_PHONE2},</if>
        <if test=" CONTACT_ADDR != null ">CONTACT_ADDR = #{CONTACT_ADDR},</if>
        <if test=" CONTACT_TYPE != null ">CONTACT_TYPE = #{CONTACT_TYPE},</if>
        <if test=" ENABLE_TYPE != null ">ENABLE_TYPE = #{ENABLE_TYPE},</if>
        <if test=" STARTTIME != null ">STARTTIME = to_timestamp(#{STARTTIME}, 'YYYY-MM-DD HH24:MI:SS'),</if>
        <if test=" ENDTIME != null ">ENDTIME = to_timestamp(#{ENDTIME}, 'YYYY-MM-DD HH24:MI:SS'),</if>
        <if test=" MAX_APPID != null ">MAX_APPID = ${MAX_APPID},</if>
        <if test=" MAX_APPSERVER != null ">MAX_APPSERVER = ${MAX_APPSERVER},</if>
        <if test=" MAX_USER != null ">MAX_USER = ${MAX_USER},</if>
        <if test=" ETC != null ">ETC = #{ETC},</if>
        <if test=" UPDATOR != null ">UPDATOR = ${UPDATOR},</if>
        <if test=" VENDOR_CODE != null ">VENDOR_CODE = #{VENDOR_CODE},</if>
        UPDATEDTIME = sysdate
    where IDX = ${IDX}
</update>
```

> insert에는 `COMPANY_TYPE`이 있는데 update에는 `CONTACT_TYPE`이 있다. 원본 그대로다.

### selectVendorCode

```xml
<select id="selectVendorCode" parameterType="java.util.HashMap" resultType="java.util.HashMap" flushCache="true">
    select * from CCFA_COMPANY
    where VENDOR_CODE = #{VENDOR_CODE}
</select>
```

### insertManagerPwPolicy

```xml
<!-- update 220610 -->
<insert id="insertManagerPwPolicy" parameterType="String">
    insert into CCFA_MANAGER_PW_POLICY (USER_ID) VALUES (#{userId})
</insert>
```

### updateManagerPwPolicy

```xml
<update id="updateManagerPwPolicy" parameterType="String">
    update CCFA_MANAGER_PW_POLICY set
        ACCOUNT_LOCK =
            CASE
                WHEN PW_FAIL_CNT = 4 then 'Y'
                ELSE 'N'
            END,
        ...
</update>
```

> 212줄 이후(`END,` 다음)가 영상에 노출되지 않았다. `PW_FAIL_CNT` 증감과 `where` 절이 이어질 것으로 보인다.

**확보하지 못한 부분**
- 1~18줄 (XML 선언, `resultMap`, `selectManager`류 쿼리의 앞부분)
- 213~223줄 (`updateManagerPwPolicy` 후반부와 `</mapper>`)

## scheduler.xml

`namespace="scheduler"` · 79줄 · 배치/스케줄러 전용

```xml
<resultMap id="hashMap" type="java.util.HashMap" autoMapping="true">
    <result column="EXCEPTION_DATA" property="EXCEPTION_DATA" jdbcType="CLOB" javaType="java.lang.String"/>
</resultMap>
```

### selectCompanyList

```xml
<select id="selectCompanyList" resultType="java.util.HashMap" flushCache="true">
    select * from CCFA_COMPANY
</select>
```

### selectManagerList

```xml
<select id="selectManagerList" resultType="java.util.HashMap" flushCache="true">
    select * from CCFA_MANAGER
</select>
```

### selectSystemProp

```xml
<select id="selectSystemProp" parameterType="java.util.HashMap" resultType="java.util.HashMap" flushCache="true">
    select a.* from CCFA_SYSTEM_PROP a
    where a.COMPANY_IDX = ${COMPANY_IDX}
</select>
```

### insertStatistics

```xml
<insert id="insertStatistics" parameterType="java.util.HashMap">
    insert into FIDO_STATISTICS (COMPANY_IDX,SERVICE_NAME,GROUPBY,AUTH_S,AUTH_F,TC_S,TC_F,REG_S,REG_F,DEREG_S,DEREG_F) values (
        ${COMPANY_IDX},
        #{SERVICE_NAME},
        #{GROUPBY},
        ${AUTH_S},
        ${AUTH_F},
        ${TC_S},
        ${TC_F},
        ${REG_S},
        ${REG_F},
        ${DEREG_S},
        ${DEREG_F}
        )
</insert>
```

### selectExceptions

```xml
<select id="selectExceptions" parameterType="java.util.HashMap" resultType="hashMap" flushCache="true">
    select a.*, (select COMPANY_NAME from CCFA_COMPANY where IDX = a.COMPANY_IDX) as COMPANY_NAME
    from CCFA_EXCEPTIONS a
    <if test=" COMPANY_IDX != null "> where a.COMPANY_IDX = ${COMPANY_IDX} </if>
</select>
```

### insertMailLog

```xml
<insert id="insertMailLog" parameterType="java.util.HashMap">
    insert into CCFA_MAILING (COMPANY_IDX, "TO", "SUBJECT", "CONTENT", STATUS, SMS_TO, SMS_CONTENT, SMS_STATUS, SENDTIME) values (
        ${COMPANY_IDX}, #{TO}, #{SUBJECT}, #{CONTENT}, 'SEND', #{SMS_TO, jdbcType=VARCHAR}, #{SMS_CONTENT, jdbcType=VARCHAR}, #{SMS_STATUS, jdbcType=VARCHAR}, #{SENDTIME}
        )
</insert>
```

> `TO`, `SUBJECT`, `CONTENT`는 예약어라 큰따옴표로 감쌌다.

### deleteExceptions

```xml
<delete id="deleteExceptions" parameterType="java.util.HashMap">
    delete from CCFA_EXCEPTIONS where IDX IN <foreach collection=" IDXS " item="it" index="index" separator="," open="(" close=")">${it}</foreach>
</delete>
```

### deleteChallengeCode

만료된 챌린지를 `CCFA_SYSTEM_PROP`의 `CHALLENGE_TERM` 값(일 단위)에 따라 정리한다.

```xml
<delete id="deleteChallengeCode">
    delete from CHALLENGE where IDX in (
        select a.IDX from CHALLENGE a, CCFA_SYSTEM_PROP b
        where b.PROP_KEY = 'CHALLENGE_TERM' and a.COMPANY_IDX = b.COMPANY_IDX and (a.CREATETIME + (to_number(b.PROP_VALUE)/(24*60*60))) &lt; sysdate
        )
</delete>
```

> `PROP_VALUE`를 `24*60*60`으로 나누므로 실제로는 **초 단위**다.

### deleteTCContent

```xml
<delete id="deleteTCContent">

</delete>
```

> 본문이 비어 있다. 원본 그대로다.

### deleteTChash

```xml
<delete id="deleteTChash">
    delete from TRANSACTIONHASH where IDX in (
        select a.IDX from TRANSACTIONHASH a, CCFA_SYSTEM_PROP b
        where b.PROP_KEY = 'CHALLENGE_TERM' and a.COMPANY_IDX = b.COMPANY_IDX and (a.CREATETIME + (to_number(b.PROP_VALUE)/(24*60*60))) &lt; sysdate
        )
</delete>
```

### updateManagerPwPolicyUnlock

30분 경과한 계정 잠금을 자동 해제한다.

```xml
<!-- update 220610 -->
<update id="updateManagerPwPolicyUnlock">
    update CCFA_MANAGER_PW_POLICY set
        ACCOUNT_LOCK = 'N',
        PW_FAIL_CNT = 0
    WHERE ACCOUNT_LOCK = 'Y' AND (sysdate-UPDATEDTIME)&gt;'0 0:30:00.0'
</update>
```

## statistics.xml

`namespace="statistics"` · 218줄 · 통계 위젯 정의와 그래프 조회

### selectStatistics

```xml
<select id="selectStatistics" parameterType="java.util.HashMap" resultType="java.util.HashMap" flushCache="true">
    select c.* from (
        select rownum as RN, b.* from (
            select a.* from (
                select
                    s.*, m.USER_ID, m.USER_NM, m.COMPANY_IDX, p.COMPANY_NAME,
                    (select MENU_NAME from CCFA_MENU where IDX = s.TARGET_IDX) "TARGET_NAME",
                    (select CASE WHEN TBL_NAME = 'FIDO_LOGS' THEN TBL_NAME||'_'||TO_CHAR(SYSDATE, 'YYYYMMDD') ELSE TBL_NAME END from CCFA_MENU where IDX = s.TARGET_IDX) "TARGET…
                    (select FIELD_NAME from CCFA_FIELDS where IDX = s.GROUP1_IDX) "GROUP1_FIELD",
                    (select FIELD_NAME from CCFA_FIELDS where IDX = s.GROUP2_IDX) "GROUP2_FIELD",
                    (select FIELD_NAME from CCFA_FIELDS where IDX = s.PRIME_FIELD_IDX) "PRIME_FIELD"
                from CCFA_STATISTICS s, CCFA_MANAGER m, CCFA_COMPANY p
                where s.OWNER_IDX = m.IDX and m.COMPANY_IDX = p.IDX
            ) a
            <include refid="basic.cfilters" />
            <if test=" offset != null and limit != null ">
                order by a.IDX desc, a.CREATEDTIME desc
            </if>
        ) b
    ) c
    <if test=" offset != null and limit != null ">
        where RN between ${offset} and (${offset} + ${limit})
    </if>
</select>
```

> `"TARGET…` 줄이 화면 밖으로 잘렸다. 별칭은 `"TARGET_TABLE"`로 추정된다
> (`selectGraph`에서 `${TARGET_TABLE}`을 쓴다).

### selectStatisticsTotalCount

```xml
<select id="selectStatisticsTotalCount" parameterType="java.util.HashMap" resultType="java.lang.Integer" flushCache="true">
    select count(*) from CCFA_STATISTICS a
    <include refid="basic.cfilters" />
</select>
```

### insertStatistics

```xml
<insert id="insertStatistics" parameterType="java.util.HashMap">
    insert into CCFA_STATISTICS (OWNER_IDX, TYPE, GROUP1_IDX, GROUP2_IDX, TARGET_IDX, GRAPH_TYPE, TITLE, CONTENT, ETC, OPEN_TYPE, LIMIT, PRIME_FIELD_IDX, REALTIME) values (
        ${OWNER_IDX},
        #{TYPE, jdbcType=VARCHAR},
        #{GROUP1_IDX, jdbcType=VARCHAR},
        #{GROUP2_IDX, jdbcType=VARCHAR},
        #{TARGET_IDX, jdbcType=VARCHAR},
        #{GRAPH_TYPE, jdbcType=VARCHAR},
        #{TITLE, jdbcType=VARCHAR},
        #{CONTENT, jdbcType=VARCHAR},
        #{ETC, jdbcType=VARCHAR},
        #{OPEN_TYPE, jdbcType=VARCHAR},
        #{LIMIT, jdbcType=VARCHAR},
        #{PRIME_FIELD_IDX, jdbcType=VARCHAR},
        #{REALTIME, jdbcType=VARCHAR}
        )
    <selectKey keyProperty="IDX" resultType="java.lang.Integer" order="AFTER">
        SELECT SEQ_CCFA_STATISTICS.currval FROM dual
    </selectKey>
</insert>
```

### updateStatistics

```xml
<update id="updateStatistics" parameterType="java.util.HashMap">
    update CCFA_STATISTICS set
        TYPE = #{TYPE, jdbcType=VARCHAR},
        GROUP1_IDX = #{GROUP1_IDX, jdbcType=VARCHAR},
        GROUP2_IDX = #{GROUP2_IDX, jdbcType=VARCHAR},
        TARGET_IDX = #{TARGET_IDX, jdbcType=VARCHAR},
        GRAPH_TYPE = #{GRAPH_TYPE, jdbcType=VARCHAR},
        TITLE = #{TITLE, jdbcType=VARCHAR},
        CONTENT = #{CONTENT, jdbcType=VARCHAR},
        ETC = #{ETC, jdbcType=VARCHAR},
        LIMIT = #{LIMIT, jdbcType=VARCHAR},
        OPEN_TYPE = #{OPEN_TYPE, jdbcType=VARCHAR},
        PRIME_FIELD_IDX = #{PRIME_FIELD_IDX, jdbcType=VARCHAR},
        REALTIME = #{REALTIME, jdbcType=VARCHAR},
        UPDATEDTIME = sysdate
    where IDX = ${IDX}
</update>
```

### deleteStatistics

```xml
<delete id="deleteStatistics" parameterType="java.util.HashMap">
    delete from CCFA_STATISTICS
    where IDX = ${IDX}
</delete>
```

### selectStatisticsFilter

```xml
<select id="selectStatisticsFilter" parameterType="java.util.HashMap" resultType="java.util.HashMap">
    select v.*, k.IDX as "FIELD_IDX", k.FIELD_TITLE as "COLUMN_TITLE" from (
        select z.*, (select TBL_NAME from CCFA_MENU where IDX = x.TARGET_IDX) as TBL_NAME
        from (select * from CCFA_STATISTICS_FILTER where STATISTICS_IDX = ${STATISTICS_IDX}) z, CCFA_STATISTICS x
        where z.STATISTICS_IDX = x.IDX
    ) v, CCFA_FIELDS k
    where v.TBL_NAME = k.FIELD_TABLE and v.COLUMN_NAME = k.FIELD_NAME
</select>
```

### insertStatisticsFilter

```xml
<insert id="insertStatisticsFilter" parameterType="java.util.HashMap">
    insert into CCFA_STATISTICS_FILTER (STATISTICS_IDX, COLUMN_NAME, OP, VALUE, TYPE) values (
        ${STATISTICS_IDX}, #{COLUMN_NAME}, #{OP}, #{VALUE}, #{TYPE, jdbcType=VARCHAR}
        )
</insert>
```

### deleteStatisticsFilter

```xml
<delete id="deleteStatisticsFilter" parameterType="java.util.HashMap">
    delete from CCFA_STATISTICS_FILTER
    where STATISTICS_IDX = ${IDX}
</delete>
```

### selectStatisticsOrder

```xml
<select id="selectStatisticsOrder" parameterType="java.util.HashMap" resultType="java.util.HashMap">
    select v.*, k.IDX as "FIELD_IDX", k.FIELD_TITLE as "COLUMN_TITLE" from (
        select z.*, (select TBL_NAME from CCFA_MENU where IDX = x.TARGET_IDX) as TBL_NAME
        from (select * from CCFA_STATISTICS_ORDER where STATISTICS_IDX = ${STATISTICS_IDX}) z, CCFA_STATISTICS x
        where z.STATISTICS_IDX = x.IDX
    ) v, CCFA_FIELDS k
    where v.TBL_NAME = k.FIELD_TABLE and v.COLUMN_NAME = k.FIELD_NAME
</select>
```

### insertStatisticsOrder

```xml
<insert id="insertStatisticsOrder" parameterType="java.util.HashMap">
    insert into CCFA_STATISTICS_ORDER (STATISTICS_IDX, COLUMN_NAME, TYPE) values (
        ${STATISTICS_IDX}, #{COLUMN_NAME}, #{TYPE}
        )
</insert>
```

### deleteStatisticsOrder

```xml
<delete id="deleteStatisticsOrder" parameterType="java.util.HashMap">
    delete from CCFA_STATISTICS_ORDER
    where STATISTICS_IDX = ${IDX}
</delete>
```

### selectTargets

```xml
<select id="selectTargets" parameterType="java.util.HashMap" resultType="java.util.HashMap">
    select * from CCFA_MENU where VISIBLE = 'true' and STATISTICS = 'Y'
</select>
```

### selectColumns

```xml
<select id="selectColumns" parameterType="java.util.HashMap" resultType="java.util.HashMap">
    select * from CCFA_FIELDS where FIELD_TABLE = (select TBL_NAME from CCFA_MENU where IDX = ${IDX})
</select>
```

### selectGraph

통계 위젯의 실제 그래프 데이터를 뽑는 쿼리다. 그룹 필드와 집계 타입이 모두 동적이다.

```xml
<select id="selectGraph" parameterType="java.util.HashMap" resultType="java.util.HashMap">
    select x.* from (
        select z.* from (
            select
                <if test=" GROUP1_FIELD != null ">a.${GROUP1_FIELD},</if>
                <if test=" GROUP2_FIELD != null ">a.${GROUP2_FIELD},</if>
                <if test=" PRIME_FIELD != null ">
                    <if test=" TYPE != 'list' ">${TYPE}(a.${PRIME_FIELD}) as "RVAL",</if>
                    <if test=" TYPE == 'list' ">a.${PRIME_FIELD},</if>
                </if>
                '' as "tmp"
            from (
                select
                    t.*,
                    to_char(t.CREATEDTIME, 'yyyy-MM-dd') as "CREATEDDATE",
                    to_char(t.CREATEDTIME, 'yyyy-MM-dd hh24') as "HOURLY"
                from ${TARGET_TABLE} t
            ) a
            <include refid="cfilters" />
            <trim prefix=" GROUP BY " prefixOverrides=",">
                <if test=" GROUP1_FIELD != null ">,${GROUP1_FIELD}</if>
                <if test=" GROUP2_FIELD != null ">,${GROUP2_FIELD}</if>
                <if test=" PRIME_FIELD != null ">
                    <if test=" TYPE == 'list' ">,${PRIME_FIELD}</if>
                </if>
            </trim>
        ) z
        <include refid="corders" />
    ) x
    <if test=" LIMIT != null ">
        where rownum &lt; (${LIMIT} + 1)
    </if>
</select>
```

### `<sql id="corder">` / `<sql id="corders">` — 정렬 조각

```xml
<sql id="corder">
    <if test=" item.COLUMN_NAME != null and item.TYPE != null">
        z.${item.COLUMN_NAME} ${item.TYPE}
    </if>
</sql>

<sql id="corders">
    <if test=" ORDERS != null ">
        <trim prefix=" ORDER BY " prefixOverrides="," suffixOverrides=",">
            <if test=" GRAPH_TYPE == 'list' "> RVAL desc, </if>
            <foreach collection="ORDERS" item="item" index="index" separator=", ">
                <include refid="corder" />
            </foreach>
        </trim>
    </if>
</sql>
```

### `<sql id="cfilter">` / `<sql id="cfilters">` — 통계 전용 필터 조각

`basic.xml`과 구조는 같지만 키 이름이 `COLUMN_NAME` / `OP` / `VALUE`로 다르다.

```xml
<sql id="cfilter">
    <if test=" item.COLUMN_NAME != null and item.VALUE != null">
        ${item.COLUMN_NAME}
        <choose>
            <when test=" item.OP == 'Equal' ">
                <if test=" item.VALUE == null "> IS NULL </if>
                <if test=" item.VALUE != null "> = #{item.VALUE} </if>
            </when>
            <when test=" item.OP == 'NotEqual' "> != #{item.VALUE} </when>
            <when test=" item.OP == 'Like' "> LIKE ('%' || #{item.VALUE} || '%') </when>
            <when test=" item.OP == 'NotLike' "> NOT LIKE ('%' || #{item.VALUE} || '%') </when>
            <when test=" item.OP == 'StartWith' "> LIKE (#{item.VALUE} || '%') </when>
            <when test=" item.OP == 'EndWith' "> LIKE ('%' || #{item.VALUE}) </when>
            <when test=" item.OP == 'Over' "> &gt;= #{item.VALUE} </when>
            <when test=" item.OP == 'Under' "> &lt;= #{item.VALUE} </when>
            <when test=" item.OP == 'Bigger' "> &gt; #{item.VALUE} </when>
            <when test=" item.OP == 'Smaller' "> &lt; #{item.VALUE} </when>
            <when test=" item.OP == 'Null' "> IS NULL </when>
            <when test=" item.OP == 'NotNull' "> IS NOT NULL </when>
            <when test=" item.OP == 'TRUE' "> IS TRUE </when>
            <when test=" item.OP == 'FALSE' "> IS FALSE </when>
            <when test=" item.OP == 'Between' "> BETWEEN to_timestamp(#{item.VALUE[0]}, 'YYYY-MM-DD HH24:MI:SS') AND to_timestamp(#{item.VALUE[1]}, 'YYYY-MM-DD HH24:MI:SS') </w…
        </choose>
    </if>
</sql>

<sql id="cfilters">
    <if test=" FILTERS != null ">
        <trim prefix="WHERE" prefixOverrides="AND |OR ">
            <foreach collection="FILTERS" item="item" index="index" separator=" AND ">
                <include refid="cfilter" />
            </foreach>
        </trim>
    </if>
</sql>
```

> `basic.cfilters`는 `filters`를 OR로 묶는데, 여기 `cfilters`는 `FILTERS`를 AND로 묶는다.
> `basic.xml`의 `cfilter`에 있는 `In` / `NotIn` / `InString` / `UnderTime`은 여기에 없다.

## 이관 시 확인할 사항

판독 과정에서 눈에 띈 것들이다. 원본 대조가 필요하다.

1. **`${}` 문자열 치환** — 테이블명·컬럼명·offset/limit 전반에 쓰인다. 사용자 입력이 닿는 경로면 인젝션 위험이 있다.
2. **`fds.FDSMonitor`의 `collection="AND_IP"`** — OR 조건군에서도 `AND_IP`를 순회한다. `OR_IP` 오기로 보인다.
3. **`fds.selectRecentLogId`** — `where COMPANY_IDX` 뒤가 비어 있다.
4. **`log.getItemByGroup`, `scheduler.deleteTCContent`** — 본문이 비어 있다.
5. **`manager.deleteSystemProp`** — delete 문인데 `<insert>` 태그다.
6. **`log.insertLog`** — 컬럼 `USER_NAME`에 파라미터 `#{USER_NM}`.
7. **`basic.deleteLisense`, `manager.ALRAM_TYPE`** — 오타가 ID·컬럼명에 굳어 있다.
8. **페이징 offset 기준 불일치** — `${offset}` / `${offset}-1` / `1+${offset}`이 혼재한다.
9. **`basic.cfilters`의 이중 WHERE** — `filters`와 `ANDfilters`가 동시에 있으면 `WHERE`가 두 번 생성된다.
10. **`manager.updateCompany`의 `CONTACT_TYPE`** — insert에는 `COMPANY_TYPE`인데 update에는 `CONTACT_TYPE`이다.

## 판독 누락 구간

영상에 노출되지 않았거나 화면 밖으로 잘린 부분이다.

| 파일 | 구간 | 사유 |
|---|---|---|
| `manager.xml` | 1~18줄 | 탭이 19줄 위치에서 열렸고 위로 스크롤하지 않음 |
| `manager.xml` | 213~223줄 | `updateManagerPwPolicy` 후반부 |
| `fds.xml` | 1~3줄 | XML 선언·DOCTYPE |
| `dashboard.xml` | 쿼리 ID 전반 | 화면에서 선명히 구분되지 않음 |
| `basic.xml` | 50줄, `cfilter`의 `Between` 분기 3줄 | 가로 스크롤 없이 잘림 |
| `manager.xml` | 90줄(`insertSystemProp`), 143줄(`insertCompany` 컬럼 목록) | 가로 스크롤 없이 잘림 |
| `statistics.xml` | 39줄(`"TARGET…`), `cfilter`의 `Between` 분기 | 가로 스크롤 없이 잘림 |
