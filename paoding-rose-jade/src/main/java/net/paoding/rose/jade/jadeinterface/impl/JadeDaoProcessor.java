package net.paoding.rose.jade.jadeinterface.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.paoding.rose.jade.jadeinterface.annotation.Dao;
import net.paoding.rose.jade.jadeinterface.impl.scanner.DAOScanner;
import net.paoding.rose.jade.jadeinterface.impl.scanner.ResourceInfo;
import net.paoding.rose.jade.jadeinterface.provider.DataAccessProvider;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs.FileName;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileSystemManager;
import org.apache.commons.vfs.FileType;
import org.apache.commons.vfs.VFS;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;

/**
 * 
 * 
 * @author 王志亮 [qieqie.wang@gmail.com]
 */
public class JadeDaoProcessor implements BeanFactoryPostProcessor, ApplicationContextAware {

    protected static final Log logger = LogFactory.getLog(JadeDaoProcessor.class);

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {

        List<Class<?>> classes;
        try {
            classes = findDaoClasses();
        } catch (IOException e) {
            throw new BeanCreationException("", e);
        }

        DataAccessProvider dataAccessProvider = (DataAccessProvider) applicationContext
                .getBean("dataAccessProvider");
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Dao.class) && clazz.isInterface()) {
                String beanName = ClassUtils.getShortNameAsProperty(clazz);

                GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
                beanDefinition.setBeanClass(DaoFactoryBean.class);
                MutablePropertyValues propertyValues = beanDefinition.getPropertyValues();
                propertyValues.addPropertyValue("dataAccessProvider", dataAccessProvider);
                propertyValues.addPropertyValue("daoClass", clazz);
                beanDefinition.setPropertyValues(propertyValues);
                beanDefinition.setAutowireCandidate(true);

                if (logger.isInfoEnabled()) {
                    logger.info("Generate dao: " + beanName + " ==> " + clazz.getName());
                }

                DefaultListableBeanFactory defaultBeanFactory = (DefaultListableBeanFactory) beanFactory;
                defaultBeanFactory.registerBeanDefinition(beanName, beanDefinition);
            }
        }
    }

    //------------------

    private List<Class<?>> daoClasses;

    public synchronized List<Class<?>> findDaoClasses() throws IOException {

        if (daoClasses == null) {
            daoClasses = new ArrayList<Class<?>>();

            DAOScanner daoScanner = DAOScanner.getDaoScanner();
            List<ResourceInfo> resources = new ArrayList<ResourceInfo>();
            resources.addAll(daoScanner.getClassesFolderResources());
            resources.addAll(daoScanner.getJarResources());

            FileSystemManager fsManager = VFS.getManager();
            for (ResourceInfo resourceInfo : resources) {
                if (resourceInfo.hasModifier("dao") || resourceInfo.hasModifier("DAO")) {
                    Resource resource = resourceInfo.getResource();
                    File resourceFile = resource.getFile();
                    FileObject rootObject = null;
                    if (resourceFile.isFile()) {
                        String path = "jar:file:" + resourceFile.getAbsolutePath() + "!/";
                        rootObject = fsManager.resolveFile(path);
                    } else if (resourceFile.isDirectory()) {
                        rootObject = fsManager.resolveFile(resourceFile.getAbsolutePath());
                    }
                    if (rootObject != null) {
                        deepScanImpl(rootObject, rootObject);
                    }
                }
            }
        }

        return new ArrayList<Class<?>>(daoClasses);
    }

    protected void deepScanImpl(FileObject rootObject, FileObject fileObject) {
        try {
            if (!fileObject.getType().equals(FileType.FOLDER)) {
                if (logger.isWarnEnabled()) {
                    logger.warn("fileObject shoud be a folder", // NL
                            new IllegalArgumentException());
                }
                return;
            }

            if ("dao".equals(fileObject.getName().getBaseName())) {
                handleWithFolder(rootObject, fileObject, fileObject);
            } else {
                FileObject[] children = fileObject.getChildren();
                for (FileObject child : children) {
                    if (child.getType().equals(FileType.FOLDER)) {
                        deepScanImpl(rootObject, child);
                    }
                }
            }

        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
    }

    protected void handleWithFolder(FileObject rootObject, FileObject matchedRootFolder,
            FileObject thisFolder) throws IOException {

        if (logger.isInfoEnabled()) {
            logger.info("Found dao folder: " + thisFolder);
        }

        FileObject[] children = thisFolder.getChildren();

        // 分两个循环，先处理类文件，再处理子目录，使日志更清晰
        for (FileObject child : children) {
            if (!child.getType().equals(FileType.FOLDER)) {
                handleDAOResource(rootObject, child);
            }
        }
        for (FileObject child : children) {
            if (child.getType().equals(FileType.FOLDER)) {
                handleWithFolder(rootObject, matchedRootFolder, child);
            }
        }
    }

    protected void handleDAOResource(FileObject rootObject, FileObject resource)
            throws FileSystemException {
        FileName fileName = resource.getName();
        String bn = fileName.getBaseName();
        if (bn.endsWith(".class") && (bn.indexOf('$') == -1)) {
            addDAOClass(rootObject, resource);
        }
    }

    private void addDAOClass(FileObject rootObject, FileObject resource) throws FileSystemException {
        String className = rootObject.getName().getRelativeName(resource.getName());
        className = StringUtils.removeEnd(className, ".class");
        className = className.replace('/', '.');
        for (int i = daoClasses.size() - 1; i >= 0; i--) {
            Class<?> clazz = daoClasses.get(i);
            if (clazz.getName().equals(className)) {
                if (logger.isInfoEnabled()) {
                    logger.info("Skip duplicated class " + className // NL
                            + " in: " + resource);
                }
                return;
            }
        }
        try {
            Class<?> clazz = Class.forName(className);
            if (clazz.isAnnotationPresent(Dao.class)) {
                daoClasses.add(clazz);
                if (logger.isInfoEnabled()) {
                    logger.info("Found class: " + className);
                }
            }
        } catch (ClassNotFoundException e) {
            logger.error("Class not found", e);
        }
    }
}