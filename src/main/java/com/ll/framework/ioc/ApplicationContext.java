package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.standard.util.Ut;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApplicationContext {
    private final String basePackage;
    private Map<String, Object> beans = new HashMap<>();

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    public void init() {
        try {
            // 패키지 경로를 파일 시스템 경로로 변환
            String path = basePackage.replace(".", "/");
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL resource = classLoader.getResource(path);
            File directory = new File(resource.toURI());

            scanDirectory(directory, basePackage);  // 탐색 시작
            // 클래스 bean 생성 후 메서드 bean 생성
            List<Object> curBeans = new ArrayList<>(beans.values());
            for(Object bean : curBeans) {
                createMethodBean(bean.getClass());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 패키지 하위를 재귀 탐색하여 @Component 어노테이션이 붙은 클래스를 beans에 등록하는 메서드
    private void scanDirectory(File directory, String packageName) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {
                String className = file.getName().replace(".class", "");
                String fullClassName = packageName + "." + className;
                try {
                    Class<?> clazz = Class.forName(fullClassName);
                    if (isComponent(clazz)) {
                        // 앞글자를 소문자로 변환하여 bean 이름으로 사용
                        String beanName = Ut.str.lcfirst(className);
                        // beans에 등록되어 있지 않다면 생성 후 등록
                        if (beans.get(beanName) == null) {
                            Object bean = createBean(clazz);
                            beans.put(beanName, bean);
                        }
                        System.out.println("Bean 등록: " + beanName);
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Component 어노테이션 여부 반환 메서드
    private boolean isComponent(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Component.class)) return true;

        for (Annotation annotation : clazz.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(Component.class)) {
                return true;
            }
        }

        return false;
    }

    // bean 객체 생성 메서드
    private Object createBean(Class<?> clazz) {
        try {
            // 해당 클래스의 생성자
            Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
            // 생성자의 필요 파라미터
            Parameter[] parameters = constructor.getParameters();
            if (parameters.length == 0) {
                // 길이가 0이면 바로 객체 생성하여 반환
                return constructor.newInstance();
            }
            // 의존성 주입을 위한 Object 배열
            Object[] args = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                // 파라미터 클래스 이름으로 beans 조회 후 없으면 재귀적으로 생성
                String name = Ut.str.lcfirst(parameters[i].getType().getSimpleName());
                Object bean = beans.get(name);
                if (bean == null) {
                    bean = createBean(parameters[i].getType());
                    beans.put(name, bean);
                }
                // 생성된 bean을 args 배열에 저장
                args[i] = bean;
            }
            // 객체를 생성하여 반환
            return constructor.newInstance(args);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 메서드 bean 객체 생성 메서드
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

    public <T> T genBean(String beanName) {
        return (T) beans.get(beanName);
    }
}
