/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2008, 2009, 2010 Etudes, Inc.
 * 
 * Portions completed before September 1, 2008
 * Copyright (c) 2007, 2008 The Regents of the University of Michigan & Foothill College, ETUDES Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.etudes.mneme.tool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.ambrosia.api.Context;
import org.etudes.ambrosia.util.ControllerImpl;
import org.etudes.mneme.api.Pool;
import org.etudes.mneme.api.PoolService;
import org.etudes.mneme.api.Question;
import org.etudes.mneme.api.QuestionService;
import org.sakaiproject.util.StringUtil;
import org.sakaiproject.util.Web;

/**
 * The /question_preview view for the mneme tool.
 */
public class QuestionPreviewView extends ControllerImpl
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(QuestionPreviewView.class);

	/** Pool Service */
	protected PoolService poolService = null;

	/** Question service. */
	protected QuestionService questionService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	public void get(HttpServletRequest req, HttpServletResponse res, Context context, String[] params) throws IOException
	{
		// we need a qid, then any number of parameters to form the return destination
		if (params.length < 3)
		{
			throw new IllegalArgumentException();
		}

		// if there's a * instead of question id, expect multiple ids at the end of the URL
		boolean multiple = "*".equals(params[2]);

		String destination = null;
		if (params.length > 3)
		{
			int len = params.length - 3;
			if (multiple)
			{
				len--;
			}
			destination = "/" + StringUtil.unsplit(params, 3, len, "/");
		}

		// if not specified, go to the main pools page
		else
		{
			destination = "/pools";
		}
		context.put("return", destination);

		if (!multiple)
		{
			String questionId = params[2];
			Question question = questionService.getQuestion(questionId);
			if (question == null)
			{
				// redirect to error
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, "/error/" + Errors.invalid)));
				return;
			}

			// security check
			if (!questionService.allowEditQuestion(question))
			{
				// redirect to error
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, "/error/" + Errors.unauthorized)));
				return;
			}

			List<Question> questions = new ArrayList<Question>();
			questions.add(question);
			context.put("questions", questions);

			// only if coming from pool_edit, we offer prev/next
			if (destination.startsWith("/pool_edit") || destination.startsWith("/pool_fix"))
			{
				figurePrevNextForPoolEdit(context, destination, question);
			}
		}

		else
		{
			List<Question> questions = new ArrayList<Question>();

			// question id's are in the params array at the end
			String qidParam = params[params.length - 1];

			// for entire pool "*pid"
			if (qidParam.startsWith("*"))
			{
				String pid = qidParam.substring(1);
				Pool pool = this.poolService.getPool(pid);
				if (pool != null)
				{
					context.put("pool", pool.getTitle());

					List<String> qids = this.questionService.getPoolQuestionIds(pool, null, null);
					for (String qid : qids)
					{
						// get the question
						Question question = this.questionService.getQuestion(qid);
						if (question != null)
						{
							// security check
							if (!questionService.allowEditQuestion(question))
							{
								// redirect to error
								res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, "/error/" + Errors.unauthorized)));
								return;
							}

							questions.add(question);
						}
					}
				}
			}

			// for list of selected questions "qid+qid+qid"
			else
			{
				String qids[] = StringUtil.split(qidParam, "+");
				for (String qid : qids)
				{
					// get the question
					Question question = this.questionService.getQuestion(qid);
					if (question != null)
					{
						// security check
						if (!questionService.allowEditQuestion(question))
						{
							// redirect to error
							res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, "/error/" + Errors.unauthorized)));
							return;
						}

						questions.add(question);
					}
				}
			}

			context.put("questions", questions);
		}

		// render
		uiService.render(ui, context);
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		super.init();
		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	public void post(HttpServletRequest req, HttpServletResponse res, Context context, String[] params) throws IOException
	{
		// read form
		String destination = uiService.decode(req, context);

		// go there!
		res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
	}

	/**
	 * @param poolService
	 *        the poolService to set
	 */
	public void setPoolService(PoolService poolService)
	{
		this.poolService = poolService;
	}

	/**
	 * Set the question service.
	 * 
	 * @param service
	 *        The question service.
	 */
	public void setQuestionService(QuestionService service)
	{
		this.questionService = service;
	}

	/**
	 * Figure the next and prev when coming from pool edit
	 * 
	 * @param context
	 *        The context.
	 * @param destination
	 *        The return path.
	 * @param question
	 *        The question.
	 */
	protected void figurePrevNextForPoolEdit(Context context, String destination, Question question)
	{
		// Note: must match the parameter and sort logic of PoolEditView.get()
		// /pool_edit/1/1A/1-30/...

		String[] params = StringUtil.split(destination, "/");
		if (params.length < 3) return;

		// pool
		String pid = params[2];
		Pool pool = this.poolService.getPool(pid);
		if (pool == null) return;

		context.put("pool", pool.getTitle());

		// sort
		String sortCode = PoolEditView.DEFAULT_SORT;
		if (!"-".equals(params[3])) sortCode = params[3];
		if ((sortCode == null) || (sortCode.length() != 2)) return;

		// get questions
		QuestionService.FindQuestionsSort sort = PoolEditView.findSort(sortCode);
		List<Question> questions = questionService.findQuestions(pool, sort, null, null, null, null, null, null);

		// figure this one's position (0 based)
		int position = 0;
		for (Question q : questions)
		{
			if (q.equals(question)) break;
			position++;
		}

		// figure prev and next, w/ wrap
		Question prev = null;
		if (position > 0)
		{
			prev = questions.get(position - 1);
		}
		else
		{
			prev = questions.get(questions.size() - 1);
		}

		Question next = null;
		if (position < questions.size() - 1)
		{
			next = questions.get(position + 1);
		}
		else
		{
			next = questions.get(0);
		}

		if (prev != null) context.put("prevQuestionId", prev.getId());
		if (next != null) context.put("nextQuestionId", next.getId());

		context.put("position", Integer.valueOf(position + 1));
		context.put("size", Integer.valueOf(questions.size()));
	}
}
