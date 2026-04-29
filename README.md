# Spring IoC-V3

> 스프링 부트의 IoC 컨테이너를 직접 구현해보는 학습 프로젝트 v3
> V1(하드코딩) → V2(어노테이션 + 리플렉션) → V3(@Bean 메서드 지원)로 단계적으로 발전


## 📌 프로젝트 개요 / Overview

V3에서는 V2의 클래스 단위 Bean 등록에서 나아가,  
`@Configuration` + `@Bean` 메서드를 통해 **외부 라이브러리 객체도 Bean으로 등록**할 수 있도록 구현했습니다.  
`-parameters` 컴파일 옵션을 활용하여 메서드 파라미터 이름으로 정확한 의존성을 주입합니다.


## 🆚 V1 → V2 → V3 비교

| | V1 | V2 | V3 |
|---|---|---|---|
| Bean 등록 방식 | 하드코딩 | @Component 계열 자동 감지 | @Bean 메서드 추가 지원 |
| 외부 라이브러리 Bean | ❌ 불가 | ❌ 불가 | ✅ @Configuration + @Bean |
| 의존성 파악 방식 | 수동 | 생성자 타입 기반 | 메서드 파라미터 이름 기반 |
| 확장성 | ❌ | ✅ | ✅✅ |

## 🧠 핵심 개념 정리

### IoC (Inversion of Control) - 제어의 역전

객체 생성, 의존성 연결, 생명 주기 관리의 **제어권**이 개발자 → 프레임워크로 역전되는 것입니다.

```java
// ❌ 개발자가 직접 제어
TestPostRepository repo = new TestPostRepository();
TestPostService service = new TestPostService(repo);

// ✅ 프레임워크가 대신 생성하고 넣어줌
public class TestPostService {
    private final TestPostRepository repo; // 누군가 넣어준다고 가정

    public TestPostService(TestPostRepository repo) {
        this.repo = repo; // 생성자로 받음
    }
}
```

### DI (Dependency Injection) - 의존성 주입

IoC를 구현하는 방법 중 하나로, 필요한 객체를 외부에서 자동으로 넣어주는 것입니다.  
`TestPostService`는 `TestPostRepository`가 없으면 동작하지 못합니다 → **의존한다**고 표현합니다.  
스프링은 어노테이션과 타입 정보를 기반으로 이 의존 관계를 자동으로 해석합니다.

```
// 1. 클래스 의존
TestFacadePostService
    ├── TestPostService       ← 의존
    │       └── TestPostRepository  ← 의존
    └── TestPostRepository    ← 의존

// 2. 메서드 의존
testBaseObjectMapper
    └── testBaseJavaTimeModule  ← 의존
```

### Bean - 컨테이너가 관리하는 객체

IoC 컨테이너가 관리하는 객체를 **Bean**이라고 합니다.  
컨테이너가 딱 1개만 만들어서 재사용하는 **싱글톤 패턴**으로 구현됩니다.

```java
// 같은 Bean을 두 번 요청해도 항상 같은 객체
TestPostService a = applicationContext.genBean("testPostService");
TestPostService b = applicationContext.genBean("testPostService");
a == b // true!
```

### Reflection - 런타임 클래스 분석

실행 중에 클래스 정보를 읽고 조작하는 Java 기능입니다.  
컴파일 시점이 아닌 **런타임에 객체 구조를 파악**하기 때문에  
어떤 의존성이 필요한지 자동으로 알아낼 수 있습니다.

```java
// 클래스 이름(문자열)만으로 생성자 파라미터 파악
Class<?> clazz = Class.forName("com.ll.domain.testPost.service.TestPostService");
Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
Parameter[] params = constructor.getParameters();
// → [TestPostRepository] 자동 파악!
```

리플렉션으로 할 수 있는 것들:
- 생성자 분석 → 의존성 파악
- 어노테이션 읽기 → Bean 등록 여부 결정
- 메서드 탐색 → `@Bean` 메서드 실행
- 필드 탐색 → 값 주입

### @Configuration + @Bean

클래스 단위 어노테이션(`@Service`, `@Repository`)으로는  
**외부 라이브러리 클래스**(`ObjectMapper`, `JavaTimeModule`)를 Bean으로 등록할 수 없습니다.  
`@Configuration` 클래스 안의 `@Bean` 메서드가 그 역할을 담당합니다.

```java
@Configuration
public class TestJacksonConfig {
    @Bean
    public JavaTimeModule testBaseJavaTimeModule() {
        return new JavaTimeModule();
    }

    @Bean
    public ObjectMapper testBaseObjectMapper(JavaTimeModule testBaseJavaTimeModule) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(testBaseJavaTimeModule); // 의존성 주입!
        return objectMapper;
    }
}
```

Bean 이름은 **메서드 이름** 그대로 사용합니다.

```
testBaseJavaTimeModule() → "testBaseJavaTimeModule"
testBaseObjectMapper()   → "testBaseObjectMapper"
```

### -parameters 컴파일 옵션

리플렉션으로 메서드 파라미터 이름을 읽으려면 이 옵션이 필요합니다.

```groovy
// build.gradle
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
```

옵션 없이는 `arg0`, `arg1` 같은 의미없는 이름이 반환되어  
파라미터 이름으로 Bean을 찾을 수 없습니다.

```java
// 옵션 없을 때
parameters[i].getName() // → "arg0" ❌

// 옵션 있을 때
parameters[i].getName() // → "testBaseJavaTimeModule" ✅
```

## ⚙️ V3 구현 흐름

```
① init() 호출
        ↓
② 패키지 스캔 → @Component 계열 클래스 Bean 등록 (V2와 동일)
        ↓
③ 등록된 Bean 목록 순회
        ↓
④ 각 Bean 클래스의 메서드 중 @Bean 어노테이션이 붙은 것 탐색
        ↓
⑤ 파라미터 없으면 → 바로 메서드 실행하여 등록
   파라미터 있으면 → 파라미터 이름으로 beans 조회
                  → 없으면 재귀적으로 @Bean 메서드 먼저 실행
                  → 있으면 주입
        ↓
⑥ 메서드 이름을 Bean 이름으로 beans Map에 저장
```

## 🔍 핵심 구현 코드

### @Bean 메서드 탐색 및 실행

```java
private void createMethodBean(Class<?> clazz) {
    try {
        // 메서드 탐색
        Method[] methods = clazz.getDeclaredMethods();
        for(Method method : methods) {
            // Bean 어노테이션 체크
            if(method.isAnnotationPresent(Bean.class)) {
                // 이미 컨테이너에 있으면 생성하지 않음
                if(beans.get(method.getName()) != null) continue;
                // 필요 파라미터 탐색
                Parameter[] parameters = method.getParameters();
                Object instance = beans.get(Ut.str.lcfirst(clazz.getSimpleName()));
                if(parameters.length == 0) {    // 필요 파라미터가 없는 경우
                    // 해당 메서드를 invoke하여 객체 생성 및 컨테이너 등록
                    Object bean = method.invoke(instance);
                    beans.put(method.getName(), bean);
                } else {    // 필요 파라미터가 있는 경우
                    // 의존성 주입을 위한 Object 배열
                    Object[] args = new Object[parameters.length];
                    for(int i = 0; i < parameters.length; i++) {
                        // 파라미터 매개변수 이름으로 beans 조회 후 없으면 재귀적으로 생성
                        String name = parameters[i].getName();
                        Object bean = beans.get(name);
                        if (bean == null) {
                            // 재귀적으로 생성하여 bean에 대입
                            createMethodBean(parameters[i].getType());
                            bean = beans.get(name);
                        }
                        // 생성된 bean을 args 배열에 저장
                        args[i] = bean;
                    }
                    // args를 이용하여 해당 메서드를 invoke하여 객체 생성 및 컨테이너 등록
                    Object methodBean = method.invoke(instance, args);
                    beans.put(method.getName(), methodBean);
                }
            }
            System.out.println("Method Bean 등록 : " + method.getName());
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

### init()에서 2단계로 분리

```java
public void init() {
    scanDirectory(directory, basePackage);  // 1단계: 클래스 Bean 등록

    List<Object> curBeans = new ArrayList<>(beans.values());
    for (Object bean : curBeans) {
        createMethodBean(bean.getClass());  // 2단계: @Bean 메서드 Bean 등록
    }
}
```

클래스 Bean이 모두 등록된 후에 `@Bean` 메서드를 처리해야  
`@Configuration` 인스턴스를 `beans`에서 꺼낼 수 있습니다.

## ✅ 테스트 결과

| 테스트 | 내용 | 결과 |
|--------|------|------|
| t1 | ApplicationContext 객체 생성 | ✅ |
| t2 | testPostService Bean 가져오기 | ✅ |
| t3 | 싱글톤 보장 | ✅ |
| t4 | testPostRepository Bean 가져오기 | ✅ |
| t5 | testPostService가 testPostRepository를 가지고 있는지 | ✅ |
| t6 | testFacadePostService가 testPostService, testPostRepository를 가지고 있는지 | ✅ |
| t7 | @Bean 메서드로 JavaTimeModule Bean 생성 | ✅ |
| t8 | JavaTimeModule에 의존하는 ObjectMapper Bean 생성 | ✅ |

---

## 💡 배운 점 & 회고

**@Bean 메서드가 필요한 이유**

`@Service`, `@Repository`는 우리가 만든 클래스에만 붙일 수 있습니다.  
`ObjectMapper`, `JavaTimeModule` 같은 **외부 라이브러리 클래스**에는 어노테이션을 붙일 수 없기 때문에 `@Configuration` + `@Bean` 방식이 필요하다는 것을 이해했습니다.

**파라미터 이름의 중요성**

처음에는 타입으로만 의존성을 파악했는데, 메서드 파라미터는 이름으로 찾는 것이 더 정확하다는 것을 알게 됐습니다.  
`-parameters` 컴파일 옵션 없이는 파라미터 이름이 `arg0`으로 나온다는 것도 직접 겪으며 배웠습니다.

**v1 → v2 → v3를 통해 느낀 것**

v1의 하드코딩이 왜 문제인지, v2의 어노테이션 스캔이 왜 필요한지, v3의 `@Bean` 메서드가 왜 존재하는지 직접 구현하며 스프링 부트의 설계 철학을 조금씩 이해하게 됐습니다.  
스프링에서 어노테이션으로 자동 관리가 되던 것을 리플렉션 + 어노테이션 + 재귀의 조합이라는 것을 알게 됐습니다.
앞으로는 프레임워크를 그냥 사용하는 것이 아닌, 내부적으로 어떻게 동작하는지 더 관심을 가지고 공부할 수 있을 것 같습니다.  
이번 과정을 통해 왜 오픈소스를 분석하면 개발 능력이 향상된다는 것인지도 직접 경험할 수 있었습니다.

