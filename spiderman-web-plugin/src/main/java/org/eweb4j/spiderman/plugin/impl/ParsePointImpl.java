package org.eweb4j.spiderman.plugin.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eweb4j.spiderman.container.Component;
import org.eweb4j.spiderman.fetcher.FetchRequest;
import org.eweb4j.spiderman.fetcher.FetchResult;
import org.eweb4j.spiderman.fetcher.Page;
import org.eweb4j.spiderman.plugin.ParsePoint;
import org.eweb4j.spiderman.plugin.util.DefaultModelParser;
import org.eweb4j.spiderman.plugin.util.ModelParser;
import org.eweb4j.spiderman.plugin.util.UrlUtils;
import org.eweb4j.spiderman.spider.SpiderListener;
import org.eweb4j.spiderman.task.Task;
import org.eweb4j.spiderman.xml.Field;
import org.eweb4j.spiderman.xml.Model;
import org.eweb4j.spiderman.xml.Rule;
import org.eweb4j.spiderman.xml.Target;
import org.eweb4j.util.CommonUtil;

public class ParsePointImpl implements ParsePoint{

//	private Task task;
	private SpiderListener listener;
//	private Target target ;
//	private Page page;
	
	public void init(Component site, SpiderListener listener) {
		this.listener = listener;
	}

	public void destroy() {
	}
	
//	public synchronized void context(Task task, Target target, Page page) throws Exception{
//		this.task = task;
//		this.target = target;
//		this.page = page;
//	}
	
	private ModelParser buildParser(FetchRequest request, Target target) {
	    ModelParser parser = null;
        if (target.getModel()!=null&&!CommonUtil.isBlank(target.getModel().getParser())) {
            try {
                Class<?> parserCls = Thread.currentThread().getContextClassLoader().loadClass(target.getModel().getParser());
                parser = (ModelParser)parserCls.newInstance();
                parser.init(request, target, listener);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        if (parser == null)
            parser = new DefaultModelParser(request, target, listener);
        
        return parser;
	}
	
	public FetchResult parse(FetchRequest request,FetchResult result) throws Exception {
		
	    //执行Before
        Map<String, Object> beforeModel =null;
        Model before = result.getTarget().getBefore();
        if (before != null && before.getField() != null && !before.getField().isEmpty()) {
            Target tgt = new Target();
            tgt.setModel(before);
            tgt.setName("before_"+result.getTarget().getName());
            tgt.setUrlRules(result.getTarget().getUrlRules());
            ModelParser beforeParser = this.buildParser(request, tgt);
            List<Map<String, Object>> beforeModels = beforeParser.parse(result.getPage());
            if (beforeModels != null && !beforeModels.isEmpty())
                beforeModel = beforeModels.get(0);
        }
        
	    ModelParser parser = this.buildParser(request, result.getTarget());
	    parser.setBeforeModel(beforeModel);
	    
	    //解析Models
	    List<Map<String, Object>> parseResults = parser.parse(result.getPage());
	    result.setModels(parseResults);
		//用来记录分页里已经解析的url
		Set<String> visitedUrls = new HashSet<String>();
		visitedUrls.add(request.task.url);
		if(result.getTarget().getUrlRules() != null)
		{
			List<Rule> rules = result.getTarget().getUrlRules().getRule();
			for (Rule rule : rules) {
				//顺序递归解析下一页的内容
				Map<String, Object> finalFields = new HashMap<String, Object>();
				parseNextPage(rule, result.getTarget(), request, result.getPage(), parseResults, visitedUrls, finalFields);
			}
		}
		return result;
	}

	//递归的关键是 Page
	@SuppressWarnings("unchecked")
	public void parseNextPage(Rule rule, Target target,FetchRequest request, Page page, List<Map<String, Object>> results, Set<String> visitedUrls, Map<String,Object> finalFields) throws Exception{
		Model mdl = rule.getNextPage();
		if (mdl == null)
			return ;

		Target tgt = new Target();
		tgt.setName(target.getName());
		tgt.setModel(mdl);

		//解析Model获得next URL
		Collection<String> nextUrls = UrlUtils.digUrls(page, request, rule, tgt, listener, finalFields);
		if (nextUrls == null || nextUrls.isEmpty())
			return ;
		String nextUrl = new ArrayList<String>(nextUrls).get(0);
		if (nextUrl == null || nextUrl.trim().length() == 0)
			return ;

		if (visitedUrls.contains(nextUrl)){
			return ;
		}
		Task nextTask = new Task(nextUrl,rule.getHttpMethod(),request.task.url,request.task.site, 0);
		FetchRequest nextreq = new FetchRequest();
		nextreq.setUrl(nextUrl);
		nextreq.setTask(nextTask);
		nextreq.setHttpMethod(rule.getHttpMethod());
		FetchResult fr = request.task.site.fetcher.fetch(nextreq);
		if (fr == null || fr.getPage() == null)
			return ;

		//记录已经访问过该url，下次不要重复访问它
		visitedUrls.add(nextUrl);

		//解析nextPage
		List<Field> isAlsoParseInNextPageFields = target.getModel().getIsAlsoParseInNextPageFields();
		if (isAlsoParseInNextPageFields == null || isAlsoParseInNextPageFields.isEmpty())
			return ;
		//构造一个model
		Model nextModel = new Model();
		nextModel.getField().addAll(isAlsoParseInNextPageFields);
		tgt.setModel(nextModel);

		ModelParser parser = null;
        if (!CommonUtil.isBlank(target.getModel().getParser())) {
            try {
                Class<?> parserCls = Thread.currentThread().getContextClassLoader().loadClass(target.getModel().getParser());
                parser = (ModelParser)parserCls.newInstance();
                parser.init(nextreq, tgt, listener);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        if (parser == null)
            parser = new DefaultModelParser(nextreq, tgt, listener);
        
		Page nextPageResult = fr.getPage();
		List<Map<String, Object>> nextMaps = parser.parse(nextPageResult);
		if (nextMaps == null)
			return ;

		for (Map<String, Object> nextMap : nextMaps){
			for (Iterator<Entry<String, Object>> it = nextMap.entrySet().iterator(); it.hasNext();){
				Entry<String, Object> e = it.next();
				String key = e.getKey();
				Object value = e.getValue();
				for (Map<String, Object> result : results){
					if (nextModel.isArrayField(key)){
						List<Object> list = (List<Object>) result.get(key);
						list.addAll((List<Object>)value);
					}else{
						StringBuilder sb = new StringBuilder();
						sb.append(result.get(key)).append("_##_").append(value);
						result.put(key, sb.toString());
					}
				}
			}
		}

		parseNextPage(rule, target, nextreq, nextPageResult, results, visitedUrls, finalFields);
	}
}
